package com.amazonaws.kafka.samples;

import com.amazonaws.services.ec2.AmazonEC2Async;
import com.amazonaws.services.ec2.model.*;
import com.amazonaws.services.elasticloadbalancingv2.AmazonElasticLoadBalancingAsync;
import com.amazonaws.services.elasticloadbalancingv2.model.DescribeLoadBalancersRequest;
import com.amazonaws.services.elasticloadbalancingv2.model.LoadBalancerStateEnum;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

class ServiceEndpoint {

    private final AmazonEC2Async ec2Client;
    private final AmazonElasticLoadBalancingAsync lbClient;
    private static final Logger logger = LogManager.getLogger(ServiceEndpoint.class);

    ServiceEndpoint(AmazonEC2Async ec2Client, AmazonElasticLoadBalancingAsync lbClient){
        this.ec2Client = ec2Client;
        this.lbClient = lbClient;
    }

    String getVPCId(String subnetId){
        Filter filter = new Filter()
                .withName("subnet-id")
                .withValues(subnetId);
        DescribeSubnetsRequest describeSubnetsRequest = new DescribeSubnetsRequest().withFilters(filter);
        return ec2Client.describeSubnets(describeSubnetsRequest).getSubnets().listIterator().next().getVpcId();
    }

    private LoadBalancerStateEnum checkLBStatus(String lbArn) throws ExecutionException, InterruptedException {
        DescribeLoadBalancersRequest describeLoadBalancersRequest = new DescribeLoadBalancersRequest()
                .withLoadBalancerArns(lbArn);
        return LoadBalancerStateEnum.fromValue(lbClient.describeLoadBalancersAsync(describeLoadBalancersRequest).get().getLoadBalancers().listIterator().next().getState().getCode());
    }

    private boolean whiteListEndpointAccounts(String allowedPrincipal, String serviceId) throws ExecutionException, InterruptedException {
        ModifyVpcEndpointServicePermissionsRequest modifyVpcEndpointServicePermissionsRequest = new ModifyVpcEndpointServicePermissionsRequest()
                .withAddAllowedPrincipals(allowedPrincipal)
                .withServiceId(serviceId);
        return ec2Client.modifyVpcEndpointServicePermissionsAsync(modifyVpcEndpointServicePermissionsRequest).get().getReturnValue();
    }

    void deleteServiceEndpoint(List<String> endpointServiceIds) throws ExecutionException, InterruptedException {
        DeleteVpcEndpointServiceConfigurationsRequest deleteVpcEndpointServiceConfigurationsRequest = new DeleteVpcEndpointServiceConfigurationsRequest()
                .withServiceIds(endpointServiceIds);
        ec2Client.deleteVpcEndpointServiceConfigurationsAsync(deleteVpcEndpointServiceConfigurationsRequest).get();
    }


    void createVpcEndpointServices(Map<String, Integer> lbArnsBrokerIdMap, String allowedPrincipal, String tableName, Map<Integer, String> brokerEndpointMap) throws ExecutionException, InterruptedException {
        for (Map.Entry<String, Integer> i : lbArnsBrokerIdMap.entrySet()) {
            if (checkLBStatus(i.getKey()).equals(LoadBalancerStateEnum.Active)){
                String serviceId = createVpcEndpointService(i, tableName, brokerEndpointMap.get(i.getValue()));
                if (whiteListEndpointAccounts(allowedPrincipal, serviceId)){
                    logger.info("Successfully whitelisted {} for Endpoint service {} \n", allowedPrincipal, serviceId);
                }
            } else {
                if (checkLBStatus(i.getKey()).equals(LoadBalancerStateEnum.Provisioning)){
                    do {
                        logger.info("NLB {} still provisioning. Waiting for 5 seconds. \n", i);
                        TimeUnit.SECONDS.sleep(5);
                    } while (checkLBStatus(i.getKey()).equals(LoadBalancerStateEnum.Provisioning));
                }
                if (checkLBStatus(i.getKey()).equals(LoadBalancerStateEnum.Failed) || checkLBStatus(i.getKey()).equals(LoadBalancerStateEnum.Active_impaired)){
                    logger.info("NLB {} is in {} state. Not creating the endpoint \n", i, checkLBStatus(i.getKey()));
                } else {
                    String serviceId = createVpcEndpointService(i, tableName, brokerEndpointMap.get(i.getValue()));
                    if (whiteListEndpointAccounts(allowedPrincipal, serviceId)){
                        logger.info("Successfully whitelisted {} for Endpoint service {} \n", allowedPrincipal, serviceId);
                    }
                }
            }
        }
    }

    private String createVpcEndpointService(Map.Entry<String, Integer> lbArnBrokerId, String tableName, String brokerEndpoint) throws ExecutionException, InterruptedException {
        CreateVpcEndpointServiceConfigurationRequest createVpcEndpointServiceConfigurationRequest = new CreateVpcEndpointServiceConfigurationRequest()
                .withAcceptanceRequired(false)
                .withClientToken(new Random().ints(10000, 10999).toString())
                .withNetworkLoadBalancerArns(lbArnBrokerId.getKey());
        CreateVpcEndpointServiceConfigurationResult createVpcEndpointServiceConfigurationResult = ec2Client.createVpcEndpointServiceConfigurationAsync(createVpcEndpointServiceConfigurationRequest).get();
        logger.info("NLBName: {} \n Service DNS name: {} \n", lbArnBrokerId.getKey().split("/")[2], createVpcEndpointServiceConfigurationResult.getServiceConfiguration().getServiceName());
        ManageEndpointServicesInfo.loadRecord(lbArnBrokerId.getValue(), createVpcEndpointServiceConfigurationResult.getServiceConfiguration().getServiceName(), lbArnBrokerId.getKey().split("/")[2], tableName, brokerEndpoint);

        return createVpcEndpointServiceConfigurationResult.getServiceConfiguration().getServiceId();
    }
}
