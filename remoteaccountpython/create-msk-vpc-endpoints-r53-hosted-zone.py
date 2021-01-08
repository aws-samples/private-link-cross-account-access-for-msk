import argparse
import random
import boto3
from botocore.exceptions import ClientError

parser = argparse.ArgumentParser()
parser.add_argument("--region", help="region where the Cloudformation template was run",default='us-east-1')
parser.add_argument("--stackName", help="the name of Cloudformation stack ")
parser.add_argument("--profile", help="the name of Cloudformation stack ",default='mskclient')
args = parser.parse_args()

region = args.region
stackName = args.stackName
profile = args.profile

session = boto3.Session(profile_name=profile)
ec2Client = session.client('ec2', args.region)

cfClient = session.client('cloudformation', args.region)

r53Client = session.client('route53', args.region)

vpcId = "vpc-6d6c3d17"
callerReference = random.randint(10000, 10999)
domainName = "kafka." + str(region) + ".amazonaws.com"

result = cfClient.describe_stacks(StackName=stackName)
for output in result['Stacks'][0]['Outputs']:
    if output['OutputKey'] == "VPCId":
        vpcId = output['OutputValue']


try:
    response = r53Client.create_hosted_zone(
        Name=domainName,
        VPC={
            'VPCRegion': str(region),
            'VPCId': vpcId
        },
        CallerReference=str(callerReference),
        HostedZoneConfig={
            'Comment': 'Hosted zone for Amazon MSK endpoints',
            'PrivateZone': True
        }
    )
except ClientError as err:
    print(err.response['Error'])







