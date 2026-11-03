package com.danasea.backend.tools;

import java.util.ArrayList;
import java.util.List;

import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.listeners.TestExecutionSummary;

public class FinancialMutationLauncher {

    public static void main(String[] args) {
        if (args.length == 0) {
            System.err.println("Usage: FinancialMutationLauncher <test-class-name>...");
            System.out.println("{\"found\":0,\"passed\":0,\"failed\":0,\"skipped\":0,\"aborted\":0,\"error\":\"No test class specified\"}");
            System.exit(1);
        }

        try {
            LauncherDiscoveryRequestBuilder requestBuilder = LauncherDiscoveryRequestBuilder.request();
            for (String className : args) {
                requestBuilder.selectors(DiscoverySelectors.selectClass(className));
            }

            LauncherDiscoveryRequest request = requestBuilder.build();
            Launcher launcher = LauncherFactory.create();
            SummaryGeneratingListener listener = new SummaryGeneratingListener();
            launcher.registerTestExecutionListeners(listener);

            launcher.execute(request);

            TestExecutionSummary summary = listener.getSummary();
            long found = summary.getTestsFoundCount();
            long succeeded = summary.getTestsSucceededCount();
            long failed = summary.getTestsFailedCount();
            long skipped = summary.getTestsSkippedCount();
            long aborted = summary.getTestsAbortedCount();

            List<String> failureDetails = new ArrayList<>();
            for (TestExecutionSummary.Failure failure : summary.getFailures()) {
                Throwable exception = failure.getException();
                String type = exception != null ? exception.getClass().getSimpleName() : "Unknown";
                String msg = exception != null && exception.getMessage() != null
                        ? exception.getMessage().replace("\"", "\\\"").replace("\n", " ")
                        : "";
                String testName = failure.getTestIdentifier().getDisplayName().replace("\"", "\\\"");
                boolean assertion = exception instanceof AssertionError && failure.getTestIdentifier().isTest()
                        && failure.getTestIdentifier().getSource().filter(MethodSource.class::isInstance)
                        .map(MethodSource.class::cast).map(method -> java.util.Arrays.stream(exception.getStackTrace())
                                .anyMatch(frame -> frame.getClassName().equals(method.getClassName())
                                        && frame.getMethodName().equals(method.getMethodName()))).orElse(false);
                failureDetails.add(String.format("{\"test\":\"%s\",\"type\":\"%s\",\"message\":\"%s\",\"assertion\":%s}", testName, type, msg, assertion));
            }

            StringBuilder json = new StringBuilder();
            json.append("{");
            json.append("\"containers_failed\":").append(summary.getContainersFailedCount()).append(",");
            json.append("\"found\":").append(found).append(",");
            json.append("\"passed\":").append(succeeded).append(",");
            json.append("\"failed\":").append(failed).append(",");
            json.append("\"skipped\":").append(skipped).append(",");
            json.append("\"aborted\":").append(aborted).append(",");
            json.append("\"failures\":[").append(String.join(",", failureDetails)).append("]");
            json.append("}");

            System.out.println(json.toString());
            System.exit(0);
        } catch (Throwable t) {
            System.err.println("Fatal execution error: " + t.getMessage());
            t.printStackTrace(System.err);
            System.out.println(String.format("{\"found\":0,\"passed\":0,\"failed\":0,\"skipped\":0,\"aborted\":0,\"error\":\"%s\"}",
                    t.getMessage() != null ? t.getMessage().replace("\"", "\\\"") : "Unknown error"));
            System.exit(1);
        }
    }
}
