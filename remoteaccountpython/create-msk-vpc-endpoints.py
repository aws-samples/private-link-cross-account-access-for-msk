import argparse
import boto3
from botocore.exceptions import ClientError

def getHostedZoneId():
    response = r53Client.list_hosted_zones()
    for hostedZone in response['HostedZones']:
        if hostedZone['Name'] == "kafka." + str(region) + ".amazonaws.com.":
            return hostedZone['Id'].split("/")[2]

def getServiceEndpoints(ddbResource):
    try:
        table = ddbResource.Table('Broker_Endpoint_Services')
        response=table.scan()
        serviceEndpoints = response['Items']
        return serviceEndpoints
    except ClientError as err:
        print(err.response['Error'])

def getSubnetIds(originalList, subnetIdToAppend):
    originalList.append(subnetIdToAppend)
    return originalList

def createVPCEndpoints(endpointService, ec2Client):
    try:
        response = ec2Client.create_vpc_endpoint(
            DryRun=False,
            VpcEndpointType='Interface',
            VpcId=vpcId,
            ServiceName=serviceEndpoint['Service_Endpoint_DNS'],
            SubnetIds=subnetIds,
            SecurityGroupIds=[
                endpointSecurityGroupId
            ],
            PrivateDnsEnabled=False,
            TagSpecifications=[
                {
                    'ResourceType': 'vpc-endpoint',
                    'Tags': [
                        {
                            'Key': 'Name',
                            'Value': 'MSK-Endpoint'
                        },
                    ]
                },
            ]
        )
        vpcEndpoint= response['VpcEndpoint']
        return vpcEndpoint
    except ClientError as err:
        print(err.response['Error'])

def createAliasRecordsetForEndpoint(hostedzoneId, endpointDNSName, aliasHostedZoneId, mskBrokerEndpoint):
    response = r53Client.change_resource_record_sets(
        HostedZoneId=hostedzoneId,
        ChangeBatch={
            'Changes': [
                {
                    'Action': 'CREATE',
                    'ResourceRecordSet': {
                        'Name': mskBrokerEndpoint,
                        'Type': 'A',
                        'AliasTarget': {
                            'HostedZoneId': aliasHostedZoneId,
                            'DNSName': endpointDNSName,
                            'EvaluateTargetHealth': True
                        }
                    }
                },
            ]
        }
    )
    return response

parser = argparse.ArgumentParser()
parser.add_argument("--region", help="region where the Cloudformation template was run",default="us-east-1")
parser.add_argument("--stackName", help="the name of Cloudformation stack ")
parser.add_argument("--roleArn", help="role ARN of role in MSK cluster account with permission to Broker Endpoints Dynamo DB table")
parser.add_argument("--profile", help="the name of Cloudformation stack ",default='mskclient')
args = parser.parse_args()

region = args.region
stackName = args.stackName
profile = args.profile
roleArn= args.roleArn

session = boto3.Session(profile_name=profile)

ec2Client = session.client('ec2', args.region)
r53Client = session.client('route53', args.region)
cfClient = session.client('cloudformation', args.region)

stsClient = session.client('sts')
try:
    assumed_role_objects=stsClient.assume_role(
        RoleArn=roleArn,
        RoleSessionName="ListBrokerEndpoints"
    )
    credentials=assumed_role_objects['Credentials']
    ddbResource=boto3.resource(
        'dynamodb',
        aws_access_key_id=credentials['AccessKeyId'],
        aws_secret_access_key=credentials['SecretAccessKey'],
        aws_session_token=credentials['SessionToken'],
    )
except ClientError as err:
    print(err.response['Error'])


result = cfClient.describe_stacks(StackName=stackName)
subnetIds = ""
endpointSecurityGroupId = ""
vpcId = ""
subnetIds = []

for output in result['Stacks'][0]['Outputs']:
    if "PrivateSubnetMSK" in output['OutputKey']:
        subnetIds = getSubnetIds(subnetIds, output['OutputValue'])
    if output['OutputKey'] == "KafkaClientInstanceSecurityGroup":
        endpointSecurityGroupId = output['OutputValue']
    if output['OutputKey'] == "VPCId":
        vpcId = output['OutputValue']

hostedzoneId = getHostedZoneId()

for serviceEndpoint in getServiceEndpoints(ddbResource):
    endpointServiceDNS = serviceEndpoint['Service_Endpoint_DNS']
    mskBrokerEndpoint = serviceEndpoint['Broker_Endpoint']
    print("Creating VPC Endpoint for Service Endpoint: " + endpointServiceDNS)
    try: 
        response = createVPCEndpoints(endpointServiceDNS, ec2Client)
        dnsentries = response['DnsEntries']
        for dnsEntry in dnsentries:
            if str(region) not in dnsEntry['DnsName'].split(".")[0]:
                endpointDNSEntry = dnsEntry
        #print(endpointDNSEntry)
        response = createAliasRecordsetForEndpoint(hostedzoneId, endpointDNSEntry['DnsName'], endpointDNSEntry['HostedZoneId'], mskBrokerEndpoint)
        #print(response)
    except ClientError as err:
        print(err.response['Error'])
