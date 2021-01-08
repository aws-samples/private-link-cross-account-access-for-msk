package com.amazonaws.kafka.samples;

import com.amazonaws.services.ec2.AmazonEC2Async;
import com.amazonaws.services.elasticloadbalancingv2.AmazonElasticLoadBalancingAsync;
import com.amazonaws.services.kafka.AWSKafkaAsync;
import com.amazonaws.services.kafka.model.ListNodesResult;
import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

public class PrivateLinkCrossAccount {

    private static String tableName = "Broker_Endpoint_Services";
    private static final Logger logger = LogManager.getLogger(PrivateLinkCrossAccount.class);

    @Parameter(names = {"--help", "-h"}, help = true)
    private boolean help = false;

    @Parameter(names = {"--region", "-reg"})
    static String region = "us-east-1";

    @Parameter(names = {"--mskClusterArn", "-mca"}, required = true)
    static String mskClusterArn;

    @Parameter(names = {"--allowedPrincipal", "-alp"}, required = true)
    private static String allowedPrincipal;

    @Parameter(names = {"--targetPort", "-tgp"})
    private static Integer targetPort = 9094;

    @Parameter(names = {"--lbListenerPort", "-llp"})
    private static Integer lbListenerPort = 9094;

    @Parameter(names = {"--deleteResources", "-del"})
    private static boolean deleteResources = false;


    private static void initialize() {
        long readCapacityUnits = 3L;
        long writeCapacityUnits = 3L;
        ManageEndpointServicesInfo.deleteTable(tableName);
        ManageEndpointServicesInfo.createTable(tableName, readCapacityUnits, writeCapacityUnits);
    }

    private static void cleanup(MSK msk, NLB nlb, ServiceEndpoint serviceEndpoint) throws ExecutionException, InterruptedException {
        logger.info("Getting Amazon MSK nodes .. \n");
        ListNodesResult mskNodes = msk.getNodes();
        List<String> nlbs = nlb.getNLBs(mskNodes);
        logger.info("Deleting Endpoint services .. \n");
        serviceEndpoint.deleteServiceEndpoint(ManageEndpointServicesInfo.queryRecords(tableName, nlbs));
        logger.info("Deleting Listeners .. \n");
        List<String> nlbArns = nlb.getNLBArns(nlbs);
        nlb.deleteListeners(nlb.getNLBListenerArns(nlbArns));
        logger.info("Deleting Target Groups .. \n");
        nlb.deleteTargetGroups(nlb.getTargetGroupArns(nlb.getTargetGroups(mskNodes)));
        logger.info("Deleting NLBs .. \n");
        nlb.deleteNLBs(nlbArns);
    }

    private static void createResources(MSK msk, NLB nlb, ServiceEndpoint serviceEndpoint) throws ExecutionException, InterruptedException {
        logger.info("Getting Amazon MSK nodes .. \n");
        ListNodesResult mskNodes = msk.getNodes();
        logger.info("Getting Amazon MSK subnets .. \n");
        List<String> subnetList = msk.getSubnetList(mskNodes);
        logger.info("Creating NLBs .. \n");
        Map<String, Integer> lbArnsBrokerIdMap = nlb.createNLBSet(msk.getBrokerIPMap(mskNodes), subnetList, targetPort, lbListenerPort, serviceEndpoint.getVPCId(subnetList.get(0)));
        logger.info("Creating Endpoint services .. \n");
        Map<Integer, String> brokerEndpointMap = msk.getBrokerEndpointMap(mskNodes);
        serviceEndpoint.createVpcEndpointServices(lbArnsBrokerIdMap, allowedPrincipal, tableName, brokerEndpointMap);
    }

    public static void main(String[] args) {

        final PrivateLinkCrossAccount privateLinkCrossAccount = new PrivateLinkCrossAccount();
        JCommander jc = JCommander.newBuilder()
                .addObject(privateLinkCrossAccount)
                .build();
        jc.parse(args);
        if (privateLinkCrossAccount.help) {
            jc.usage();
            return;
        }

        final AWSKafkaAsync mskClient = Clients.createMSKClient();
        final AmazonEC2Async ec2Client = Clients.createEC2Client();
        final AmazonElasticLoadBalancingAsync lbClient = Clients.createLoadBalancingClient();
        final MSK msk = new MSK(mskClient);
        final NLB nlb = new NLB(lbClient);
        final ServiceEndpoint serviceEndpoint = new ServiceEndpoint(ec2Client, lbClient);
        int exitStatus = 0;

        long startTime = System.nanoTime();
        logger.info("Start time: {} \n", TimeUnit.NANOSECONDS.toMillis(startTime));

        try {
            if (deleteResources) {
                cleanup(msk, nlb, serviceEndpoint);
            } else {
                logger.info("Initializing DynamoDB table .. \n");
                initialize();
                createResources(msk, nlb, serviceEndpoint);
            }
        } catch (Exception e) {
            logger.error(Util.stackTrace(e));
            exitStatus = 1;
        } finally {
            mskClient.shutdown();
            ec2Client.shutdown();
            lbClient.shutdown();
        }
        long endTime = System.nanoTime();
        logger.info("End Timestamp {}\n", TimeUnit.NANOSECONDS.toMillis(System.nanoTime()));
        long executionTime = endTime - startTime;
        logger.info("Execution time in milliseconds: {} \n", TimeUnit.NANOSECONDS.toMillis(executionTime));
        System.exit(exitStatus);

    }

}
