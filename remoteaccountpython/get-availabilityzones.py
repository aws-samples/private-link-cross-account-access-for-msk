import boto3
import sys
import argparse
import requests
from botocore.exceptions import ClientError

def getAZsfromService(serviceEndpoints, ec2Client):
    for serviceEndpoint in serviceEndpoints:
        endpointDNS = serviceEndpoint['Service_Endpoint_DNS']
        serviceEndpointDescription = ec2Client.describe_vpc_endpoint_services(
            DryRun=False,
            ServiceNames= [endpointDNS],
        )
        for  v in serviceEndpointDescription['ServiceDetails']:
            for az in v['AvailabilityZones']:
                AZs.add(az)
    return AZs 

def getAZId(AZ, ec2Client):
    result = ec2Client.describe_availability_zones(ZoneNames=[
        AZ,
    ])
    for AZdetail in result['AvailabilityZones']:
        zoneId = AZdetail['ZoneId']
    return zoneId

def getAZfromId(zoneId, ec2Client):
    result = ec2Client.describe_availability_zones(ZoneIds=[
        zoneId,
    ])
    for AZdetail in result['AvailabilityZones']:
        zone = AZdetail['ZoneName']
    return zone
    
parser = argparse.ArgumentParser()
parser.add_argument("--region", help="region where the Cloudformation template was run", default="us-east-1")
parser.add_argument("--profile", help="Profile to use for AWS credentials", default="mskclient")
parser.add_argument("--roleArn", help="role ARN of role in MSK cluster account with permission to Broker Endpoints Dynamo DB table")
args = parser.parse_args()

region = args.region
roleArn= args.roleArn
profile = args.profile

remoteAZs = set()
AZs = set()

session = boto3.session.Session(profile_name=profile)
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
    ec2ClientRemote=boto3.client(
        'ec2',
        aws_access_key_id=credentials['AccessKeyId'],
        aws_secret_access_key=credentials['SecretAccessKey'],
        aws_session_token=credentials['SessionToken'],
    )
    table = ddbResource.Table('Broker_Endpoint_Services')
    response=table.scan()
    serviceEndpoints = response['Items']
except ClientError as err:
    print(err.response['Error'])

remoteAZs = getAZsfromService(serviceEndpoints, ec2ClientRemote)
for AZ in remoteAZs:
    zoneId = getAZId(AZ, ec2ClientRemote)
    print(getAZfromId(zoneId, session.client('ec2')))
    
