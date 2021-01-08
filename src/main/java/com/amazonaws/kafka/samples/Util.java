package com.amazonaws.kafka.samples;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.List;
import java.util.ListIterator;

class Util {
    private static final Logger logger = LogManager.getLogger(Util.class);

    static String stackTrace(Throwable e) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        e.printStackTrace(pw);
        return sw.toString();
    }

    static String reverseDNS(String dnsName){
        List<String> dnsNameParts = Arrays.asList(dnsName.split("\\."));
        ListIterator listIterator = dnsNameParts.listIterator(dnsNameParts.size());
        String reverseDNSName = "";
        while (listIterator.hasPrevious()) {
            reverseDNSName = reverseDNSName.concat(listIterator.previous().toString()).concat(".");
        }
        return reverseDNSName.substring(0, reverseDNSName.length() - 1);
    }
}
