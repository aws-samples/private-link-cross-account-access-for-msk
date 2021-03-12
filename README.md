## How to setup Apache Kafka client and connect it to Amazon MSK cluster running in remote account using Private link (Multiple NLBs)

You will need access to two AWS accounts for this setup:
- Customer Account A: Account in which Amazon MSK Cluster will be setup.
- Customer Account B: Account in which Apache Kafka client will be setup.

You can repeat the steps for Customer Account B for any number of accounts you want to add as consumer for Amazon MSK cluster running in Customer Account A

### 1. Setup Amazon MSK Cluster in Account A
Note: Skip this step if you already have an Amazon MSK Cluster setup

In Account A, Deploy the CloudFormation template
    
    aws cloudformation deploy \
        --stack-name MSKCluster \
        --template-file cftemplates/MSKClusterWithVPC.yml \
        --capabilities CAPABILITY_IAM
 
   

### 2. Setup NLBs and Endpoint service in Account A

Package the jar file and run it to create the NLB, Target groups, VPC endpoint service for each NLB and Dynamo DB table with this information

    java -jar PrivateLinkCrossAccount-1.0-SNAPSHOT.jar \
        --mskClusterArn <cluster_arn> --region <region_name> \
        --allowedPrincipal <role_arn> --targetPort <port_num> \
        --lbListenerPort <port_num>

    

   mskClusterArn is the ARN of you MSK cluster (required), 
   allowedPrincipal is the identity principal in Account B that has access to the endpoint service in Account A, and can be IAM users, IAM roles or AWS Accounts,
   region is the region your cluster is in (assumes us-east-1 if not provided), 
   targetPort is the port your MSK cluster Nodes are listening on (defaults to 9094), 
   lbListenerPort is the port that NLB listeners should listen on (defaults to 9094)

### 3. Setup role in Customer Account A to give read only access to Dynamo DB table created in above step to user in Customer Account B 

    python createBrokerEndpointsDDBReadOnyRole.py --profile <cluster_account_profile_name> \
        --arn <user_arn>

### 4. In Customer Account B, Get Availability zone information of the Broker nodes for Amazon MSK Cluster

    python remoteaccountpython/get-availabilityzones.py --region <region_name> \
        --profile <client_account_profile_name> \ 
        --roleArn <roleARN_from_previous_command>


   Note: This command is to be run with AWS CLI profile for Customer Account B
   Here roleARN is the ARN received after running previous command
   This will output the availability zones in which Customer Account A MSK Cluster has the Broker Nodes. Note that because you are running this using the Customer B account profile, the availability zone Names will map to same availability zone IDs and hence can be used directly to setup subnets for Apache kafka clients

### 5. Setup Apache Kafka Client instance in Customer Account B

In Account B:

   1. Deploy the CloudFormation template to setup VPC that will host the Apache Kafka Client

        ```shell
        aws cloudformation deploy \
            --stack-name MSKClientVPC \
            --template-file cftemplates/MSKVPC.yml \
            --parameter-overridesPublicSubnet1AZ=<AZ1_name> PrivateSubnet1AZ=<AZ1_name> PrivateSubnet2AZ=<AZ2_name> PrivateSubnet3AZ=<AZ3_name> \
            --profile <mskclient_profile_name>
        ```
    
   Here Parameter value for AZ names is the output from previous command. Note than the cloud formation template will not fail but create subnet in random AZs if these params are not passed. However, for the Apache Kafka client instance to be able to talk to the remote Amazon MSK cluster via all of the Endpoints setup in cluster account, it is important to pass these params.

   2. Deploy the CloudFormation template to setup the Apache Kafka Client instance

        ```shell
        aws cloudformation deploy \
            --stack-name MSKClient \
            --template-file cftemplates/MSKBastionKafkaClientInstance.yml \
            --parameter-overrides  KeyName=<ec2_keypair> \
            --capabilities CAPABILITY_IAM --profile <mskclient_profile_name>
        ```

   3. Create a Route53 private hosted Zone in client account.

        ```shell
        python remoteaccountpython/create-msk-vpc-endpoints-r53-hosted-zone.py --region <region_name> --stackName MSKClient --profile <mskclient_profile_name>
        ```

   4. Create VPC Endpoints in client account and Resource RecordSets of type ‘A’ for each broker dns name - Alias pointing to the VPC Endpoint so that requests for each broker dns endpoints are directed to the associated endpoint.
        
        ```shell
        python remoteaccountpython/create-msk-vpc-endpoints.py --region <region_name> \
            --roleArn <roleARN_from_step_3> --stackName MSKClient \
            --profile <mskclient_profile_name>
        ```
 
Note: the setup process for this pattern can be fully automated to make sure even if Amazon MSK cluster is scaled out (e.g. adding more brokers), the appropriate resources are created in both the cluster and client accounts to make sure the solution continues to work as expected. 
 - A lambda function continuously monitors the MSK cluster for any change.
 - If a new broker is added to the MSK cluster, lambda function creates the required NLB and endpoint services. It also updates a DynamoDB table with the new changes
 - A lambda function is setup in client account which listen to the DynamoDB table stream
in cluster account. If a new record is added to the DynamoDB, the lambda function creates the required endpoint interface and make the required changes into Routh53 hosted zone and record set.


