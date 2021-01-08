package com.amazonaws.kafka.samples;

import com.amazonaws.services.elasticloadbalancingv2.AmazonElasticLoadBalancingAsync;
import com.amazonaws.services.elasticloadbalancingv2.model.*;
import com.amazonaws.services.kafka.model.ListNodesResult;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class NLB {

    private final AmazonElasticLoadBalancingAsync lbClient;
    private static final Logger logger = LogManager.getLogger(NLB.class);

    NLB(AmazonElasticLoadBalancingAsync lbClient){
        this.lbClient = lbClient;
    }

    private void modifyLBAttributes(String lbArn, Collection<LoadBalancerAttribute> loadBalancerAttributeCollection) throws ExecutionException, InterruptedException {
        ModifyLoadBalancerAttributesRequest modifyLoadBalancerAttributesRequest = new ModifyLoadBalancerAttributesRequest()
                .withLoadBalancerArn(lbArn)
                .withAttributes(loadBalancerAttributeCollection);
        lbClient.modifyLoadBalancerAttributesAsync(modifyLoadBalancerAttributesRequest).get();
    }

    private String createTargetGroup(String vpcId, Integer targetPort, String tgName) throws ExecutionException, InterruptedException {
        CreateTargetGroupRequest createTargetGroupRequest = new CreateTargetGroupRequest()
                .withTargetType(TargetTypeEnum.Ip)
                .withHealthCheckEnabled(true)
                .withPort(targetPort)
                .withProtocol(ProtocolEnum.TCP)
                .withVpcId(vpcId)
                .withName(tgName);

        return lbClient.createTargetGroupAsync(createTargetGroupRequest).get().getTargetGroups().listIterator().next().getTargetGroupArn();
    }

    private void deleteTargetGroup(String targetGroupArn) throws ExecutionException, InterruptedException {
        DeleteTargetGroupRequest deleteTargetGroupRequest = new DeleteTargetGroupRequest()
                .withTargetGroupArn(targetGroupArn);
        lbClient.deleteTargetGroupAsync(deleteTargetGroupRequest).get();
    }

    void deleteTargetGroups(List<String> targetGroupsArns){
        targetGroupsArns.forEach(i -> {
            try {
                logger.info("Deleting Target Group with Arn: {} \n", i);
                deleteTargetGroup(i);
            } catch (ExecutionException| InterruptedException e) {
                logger.error(Util.stackTrace(e));
                throw new RuntimeException(String.format("Could not delete Target Group with Arn: %s \n", i));
            }
        });

    }

    private void deleteListener(String listenerArn) throws ExecutionException, InterruptedException {
        DeleteListenerRequest deleteListenerRequest = new DeleteListenerRequest()
                .withListenerArn(listenerArn);
        lbClient.deleteListenerAsync(deleteListenerRequest).get();
    }

    void deleteListeners(List<String> nlbListenerArns){
        nlbListenerArns.forEach(i -> {
            try {
                logger.info("Deleting Listener with Arn: {} \n", i);
                deleteListener(i);
            } catch (ExecutionException| InterruptedException e) {
                logger.error(Util.stackTrace(e));
                throw new RuntimeException(String.format("Could not delete Listener with Arn: %s \n", i));
            }
        });

    }

    List<String> getNLBListenerArns(List<String> nlbArns){
        List<String> nlbListenerArns = new ArrayList<>();
        nlbArns.forEach(i -> {
            DescribeListenersRequest describeListenersRequest = new DescribeListenersRequest()
                    .withLoadBalancerArn(i);
            try {
                lbClient.describeListenersAsync(describeListenersRequest).get().getListeners().forEach(k -> nlbListenerArns.add(k.getListenerArn()));
            } catch (ExecutionException| InterruptedException e) {
                logger.error(Util.stackTrace(e));
                throw new RuntimeException(String.format("Could not get listeners for NLB Arn: %s \n", i));
            }
        });
        return nlbListenerArns;
    }

    private void deleteNLB(String lbArn) throws ExecutionException, InterruptedException {
        DeleteLoadBalancerRequest deleteLoadBalancerRequest = new DeleteLoadBalancerRequest()
                .withLoadBalancerArn(lbArn);
        lbClient.deleteLoadBalancerAsync(deleteLoadBalancerRequest).get();
    }

    void deleteNLBs(List<String> nlbArns){
        nlbArns.forEach(i -> {
            try {
                logger.info("Deleting NLB with Arn: {} \n", i);
                deleteNLB(i);
            } catch (ExecutionException| InterruptedException e) {
                logger.error(Util.stackTrace(e));
                throw new RuntimeException(String.format("Could not delete NLB with Arn: %s \n", i));
            }
        });
    }

    List<String> getTargetGroups(ListNodesResult mskNodes){
        List<String> targetGroups = new ArrayList<>();
        mskNodes.getNodeInfoList().forEach(i -> targetGroups.add("TG-MSKBroker-"  + i.getBrokerNodeInfo().getClientVpcIpAddress().replace(".", "-") + "-" + i.getBrokerNodeInfo().getBrokerId().intValue()));
        return targetGroups;
    }

    List<String> getTargetGroupArns(List<String> targetGroups) throws ExecutionException, InterruptedException {
        List<String> targetGroupsArns = new ArrayList<>();
        DescribeTargetGroupsRequest describeTargetGroupsRequest = new DescribeTargetGroupsRequest()
                .withNames(targetGroups);
        lbClient.describeTargetGroupsAsync(describeTargetGroupsRequest).get().getTargetGroups().forEach(i -> targetGroupsArns.add(i.getTargetGroupArn()));
        return targetGroupsArns;
    }

    List<String> getNLBs(ListNodesResult mskNodes){
        List<String> nlbs = new ArrayList<>();
        mskNodes.getNodeInfoList().forEach(i -> nlbs.add("NLB-MSKBroker-"  + i.getBrokerNodeInfo().getClientVpcIpAddress().replace(".", "-") + "-" + i.getBrokerNodeInfo().getBrokerId().intValue()));
        return nlbs;
    }

    List<String> getNLBArns(List<String> nlbs) throws ExecutionException, InterruptedException {
        List<String> nlbArns = new ArrayList<>();
        DescribeLoadBalancersRequest describeLoadBalancersRequest = new DescribeLoadBalancersRequest()
                .withNames(nlbs);
      lbClient.describeLoadBalancersAsync(describeLoadBalancersRequest).get().getLoadBalancers().forEach(i -> nlbArns.add(i.getLoadBalancerArn()));
      return nlbArns;
    }


    private void registerTargets(String targetGroupArn, Collection<TargetDescription> targetDescriptionCollection) throws ExecutionException, InterruptedException {
        RegisterTargetsRequest registerTargetsRequest = new RegisterTargetsRequest()
                .withTargetGroupArn(targetGroupArn)
                .withTargets(targetDescriptionCollection);
        lbClient.registerTargetsAsync(registerTargetsRequest).get();
    }

    private TargetDescription getTargetDescription(String IpAddress, Integer targetPort){
        return new TargetDescription().withId(IpAddress).withPort(targetPort);
    }

    private void createLBListener(String lbArn, Integer lbPort, String targetGroupArn) throws ExecutionException, InterruptedException {
        CreateListenerRequest createListenerRequest = new CreateListenerRequest()
                .withLoadBalancerArn(lbArn)
                .withPort(lbPort)
                .withProtocol("TCP")
                .withDefaultActions(new Action()
                        .withType(ActionTypeEnum.Forward)
                        .withTargetGroupArn(targetGroupArn));
        lbClient.createListenerAsync(createListenerRequest).get();
    }

    private String createNLB(String lbName, List<String> subnetList) throws ExecutionException, InterruptedException {
        CreateLoadBalancerRequest createLoadBalancerRequest = new CreateLoadBalancerRequest()
                .withName(lbName)
                .withType(LoadBalancerTypeEnum.Network)
                .withSubnets(subnetList)
                .withScheme(LoadBalancerSchemeEnum.Internal);
        logger.info("Starting Creation of NLB {} \n", lbName);
        Future<CreateLoadBalancerResult> futureCreateLoadBalancerResult = lbClient.createLoadBalancerAsync(createLoadBalancerRequest);

        while (!futureCreateLoadBalancerResult.isDone()){
           logger.info("NLB {} is not done creating yet.Sleeping for 2 seconds.. \n", lbName);
            TimeUnit.SECONDS.sleep(2);
        }

        logger.info("NLB {} created. \n", lbName);
        return futureCreateLoadBalancerResult.get().getLoadBalancers().listIterator().next().getLoadBalancerArn();
    }

    Map<String, Integer> createNLBSet(Map<Double, String> brokerIPMap, List<String> subnetList, Integer targetPort, Integer lbListenerPort, String vpcId) throws ExecutionException, InterruptedException {

        Map<String, Integer> lbArnsBrokerIdMap = new HashMap<>();

        for (Map.Entry<Double, String> i : brokerIPMap.entrySet()) {
            int brokerId = i.getKey().intValue();
            String lbName = "NLB-MSKBroker-" + i.getValue().replace(".", "-") + "-" + brokerId;
            String tgName = "TG-MSKBroker-"  + i.getValue().replace(".", "-") + "-" +  brokerId;
            String lbArn = createNLB(lbName, subnetList);

            lbArnsBrokerIdMap.put(lbArn, brokerId);
            modifyLBAttributes(lbArn, getLoadBalancerAttributeCollection());
            String targetGroupArn = createTargetGroup(vpcId, targetPort, tgName);
            createLBListener(lbArn, lbListenerPort, targetGroupArn);
            registerTargets(targetGroupArn, Collections.singletonList(getTargetDescription(i.getValue(), targetPort)));
        }
        return lbArnsBrokerIdMap;
    }

    private Collection<LoadBalancerAttribute> getLoadBalancerAttributeCollection(){
        return Stream.of(
                new LoadBalancerAttribute()
                        .withKey("load_balancing.cross_zone.enabled")
                        .withValue("true")).collect(Collectors.toCollection(ArrayList::new));

    }


}
