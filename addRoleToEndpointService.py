import boto3
import sys
import argparse
import requests
import json
from botocore.exceptions import ClientError

parser = argparse.ArgumentParser()
parser.add_argument("--region", help="region where the Cloudformation template was run", default="us-east-1")
parser.add_argument("--arn", required=True, help="ARN of remote role/user to be whitelisted on Endpoint service")
parser.add_argument("--profile", help="Profile to use for AWS credentials", default="default")
args = parser.parse_args()
profile = args.profile
remote_user_arn=args.arn
region = args.region

session = boto3.session.Session(profile_name=profile)
ec2Client = session.client('ec2')
ddbResource = session.resource('dynamodb')
try:    
    table = ddbResource.Table('Broker_Endpoint_Services')
    response=table.scan()
    serviceEndpoints = response['Items']
except ClientError as err:
    print(err.response['Error'])

for serviceEndpoint in serviceEndpoints:
        endpointDNS = serviceEndpoint['Service_Endpoint_DNS']
        #response = ec2Client.modify_vpc_endpoint_service_permissions(
        response = ec2Client.describe_vpc_endpoint_services(
            ServiceNames=[
                endpointDNS,
            ]
        )
        for service in response['ServiceDetails']:
            serviceId = service['ServiceId']
            response = ec2Client.modify_vpc_endpoint_service_permissions(
                DryRun=False,
                ServiceId=serviceId,
                AddAllowedPrincipals=[
                    remote_user_arn,
                ]   
            )
            print response