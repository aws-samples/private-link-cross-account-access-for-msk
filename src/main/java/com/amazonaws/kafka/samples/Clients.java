package com.amazonaws.kafka.samples;

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.cloudformation.AmazonCloudFormationAsync;
import com.amazonaws.services.cloudformation.AmazonCloudFormationAsyncClientBuilder;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBAsync;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBAsyncClientBuilder;
import com.amazonaws.services.ec2.AmazonEC2Async;
import com.amazonaws.services.ec2.AmazonEC2AsyncClientBuilder;
import com.amazonaws.services.elasticloadbalancingv2.AmazonElasticLoadBalancingAsync;
import com.amazonaws.services.elasticloadbalancingv2.AmazonElasticLoadBalancingAsyncClientBuilder;
import com.amazonaws.services.kafka.AWSKafkaAsync;
import com.amazonaws.services.kafka.AWSKafkaAsyncClientBuilder;
import com.amazonaws.services.s3.model.Region;
import org.apache.kafka.clients.admin.AdminClient;

import java.util.Map;

public class Clients {

    private static final DefaultAWSCredentialsProviderChain defaultAWSCredentialsProviderChain = new DefaultAWSCredentialsProviderChain();

    private static final AWSKafkaAsyncClientBuilder builder = AWSKafkaAsyncClientBuilder.standard()
            .withRegion(Regions.fromName(PrivateLinkCrossAccount.region))
            .withCredentials(defaultAWSCredentialsProviderChain);

    private final AmazonCloudFormationAsyncClientBuilder amazonCloudFormationAsyncBuilder = AmazonCloudFormationAsyncClientBuilder.standard()
            .withRegion(Regions.fromName(PrivateLinkCrossAccount.region))
            .withCredentials(defaultAWSCredentialsProviderChain);

    private static final AmazonEC2AsyncClientBuilder amazonEC2AsyncClientBuilder = AmazonEC2AsyncClientBuilder.standard()
            .withRegion(Regions.fromName(PrivateLinkCrossAccount.region))
            .withCredentials(defaultAWSCredentialsProviderChain);

    private static final AmazonElasticLoadBalancingAsyncClientBuilder amazonElasticLoadBalancingAsyncClientBuilder = AmazonElasticLoadBalancingAsyncClientBuilder.standard()
            .withRegion(Regions.fromName(PrivateLinkCrossAccount.region))
            .withCredentials(defaultAWSCredentialsProviderChain);

    private static final AmazonDynamoDBAsyncClientBuilder amazonDynamoDBAsyncClientBuilder = AmazonDynamoDBAsyncClientBuilder.standard()
            .withRegion(Regions.fromName(PrivateLinkCrossAccount.region))
            .withCredentials(defaultAWSCredentialsProviderChain);

    public AmazonCloudFormationAsync createCloudFormationClient() {
        return amazonCloudFormationAsyncBuilder.build();
    }

    static AWSKafkaAsync createMSKClient() {
        return builder.build();
    }

    static AmazonEC2Async createEC2Client(){
        return amazonEC2AsyncClientBuilder.build();
    }

    static AmazonElasticLoadBalancingAsync createLoadBalancingClient(){
        return amazonElasticLoadBalancingAsyncClientBuilder.build();
    }

    static AmazonDynamoDBAsync createDynamoDBClient(){
        return amazonDynamoDBAsyncClientBuilder.build();
    }



    public AdminClient createKafkaAdminClient(Map<String, Object> config) {
        return AdminClient.create(config);
    }
}
