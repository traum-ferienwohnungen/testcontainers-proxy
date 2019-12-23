package com.traum.mountebank;

import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.OutputFrame;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;

public class MountebankContainer extends GenericContainer<MountebankContainer> {

    static final int MOUNTEBANK_API_PORT = 2525;

    public MountebankContainer(Integer... imposterPorts) {
        this(new Slf4jLogConsumer(LoggerFactory.getLogger(MountebankContainer.class)), imposterPorts);
    }

    public MountebankContainer(Logger logger, Integer... imposterPorts) {
        this(new Slf4jLogConsumer(logger), imposterPorts);
    }

    public MountebankContainer(Consumer<OutputFrame> logConsumer, Integer... imposterPorts) {
        super("andyrbell/mountebank");

        final Integer[] allPorts = new Integer[imposterPorts.length + 1];
        System.arraycopy(imposterPorts, 0, allPorts, 1, imposterPorts.length);
        allPorts[0] = MOUNTEBANK_API_PORT;

        withReuse(true);
        withExposedPorts(allPorts);
        withCommand("mb", "--debug");
        withLogConsumer(logConsumer);
        waitingFor(Wait.forLogMessage(".*now taking orders.*", 1));
    }
}
