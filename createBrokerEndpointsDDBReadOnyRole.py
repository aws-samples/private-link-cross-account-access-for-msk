import boto3
import sys
import argparse
import requests
import json
from botocore.exceptions import ClientError

parser = argparse.ArgumentParser()
parser.add_argument("--region", help="region where the Cloudformation template was run", default="us-east-1")
parser.add_argument("--arn", required=True, help="ARN of remote user to be added as Principal in role")
parser.add_argument("--profile", help="Profile to use for AWS credentials", default="default")
args = parser.parse_args()
profile = args.profile
remote_user_arn=args.arn
region = args.region

session = boto3.session.Session(profile_name=profile)
iam = session.client('iam')
dynamodb_client = session.client('dynamodb',region)
resource_arn = ""

def get_dynamo_db_arn( table_name ):
    try:
        response = dynamodb_client.describe_table(TableName=table_name)
        resource_arn = response['Table']['TableArn']
    except ClientError as err:
            raise err
    return resource_arn       

def create_read_only_policy (resource_arn): 
    mskBrokerEndpointsDDBReadOnlyPolicy = {
        "Version": "2012-10-17",
        "Statement": [
            {
                "Action": [
                    "dynamodb:BatchGetItem",
                    "dynamodb:Describe*",
                    "dynamodb:GetItem",
                    "dynamodb:List*",
                    "dynamodb:Query",
                    "dynamodb:Scan"
                ],
                "Effect": "Allow",
                "Resource": resource_arn,
                "Sid": "BrokerEndpointsDynamodb"
            },
            {
            "Sid": "VPCEndpointServices",
            "Effect": "Allow",
            "Action": [
                "ec2:DescribeAvailabilityZones",
                "ec2:DescribeVpcEndpointServices"
            ],
            "Resource": "*"
            }
        ]
    }
    policy_name="MSKBrokerEndpointsDDBReadOnlyPolicy"
    try: 
        response = iam.create_policy(
            PolicyName=policy_name,
            PolicyDocument=json.dumps(mskBrokerEndpointsDDBReadOnlyPolicy)
        )
        policy_arn = response['Policy']['Arn']
    except ClientError as err:
        if err.response['Error']['Code'] == 'EntityAlreadyExists':
            print('Policy already exists... Retrieving policy arn')
            account_id = session.client('sts').get_caller_identity()['Account']
            policy_arn = 'arn:aws:iam::' + account_id + ':policy/' + policy_name
        else:
            raise err
    return policy_arn

def create_role_with_policy(policy_arn):
    assumeRolePolicyDocumentForRemote= {
    "Version": "2012-10-17",
    "Statement": [
        {
        "Effect": "Allow",
        "Principal": {
            "AWS": remote_user_arn
        },
        "Action": "sts:AssumeRole",
        "Condition": {}
        }
    ]
    }

    role_name="MSKRemoteAccountBrokerEndpointsRole"
    try:
        response = iam.create_role(
            RoleName=role_name,
            AssumeRolePolicyDocument=json.dumps(assumeRolePolicyDocumentForRemote)
        )
        role_arn = response
    except ClientError as err:
        if err.response['Error']['Code'] == 'EntityAlreadyExists':
            print('Role already exists... Attaching policy...')
        else:
            raise err
    try:
        iam.attach_role_policy(
            PolicyArn=policy_arn,
            RoleName=role_name
        )
        response= iam.get_role(RoleName = role_name)
        role_arn = response['Role']['Arn']
    except ClientError as err:
            raise err
    return role_arn

try: 
    resource_arn = get_dynamo_db_arn("Broker_Endpoint_Services")
    policy_arn = create_read_only_policy(resource_arn)
    role_arn = create_role_with_policy(policy_arn)
    print(role_arn)
except ClientError as err:
    sys.exit(err)