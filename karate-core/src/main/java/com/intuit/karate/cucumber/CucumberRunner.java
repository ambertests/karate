/*
 * The MIT License
 *
 * Copyright 2017 Intuit Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.intuit.karate.cucumber;

import com.intuit.karate.ScriptEnv;
import cucumber.runtime.Backend;
import cucumber.runtime.Runtime;
import cucumber.runtime.RuntimeGlue;
import cucumber.runtime.RuntimeOptions;
import cucumber.runtime.RuntimeOptionsFactory;
import cucumber.runtime.StopWatch;
import cucumber.runtime.UndefinedStepsTracker;
import cucumber.runtime.io.MultiLoader;
import cucumber.runtime.io.ResourceLoader;
import cucumber.runtime.model.CucumberFeature;
import cucumber.runtime.xstream.LocalizedXStreams;
import gherkin.formatter.Formatter;
import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
public class CucumberRunner {

    private static final Logger logger = LoggerFactory.getLogger(CucumberRunner.class);

    private final ClassLoader classLoader;
    private final RuntimeOptions runtimeOptions;
    private final ResourceLoader resourceLoader;
    private final List<FeatureFile> featureFiles;

    public CucumberRunner(Class clazz) {
        logger.debug("init test class: {}", clazz);
        classLoader = clazz.getClassLoader();
        RuntimeOptionsFactory runtimeOptionsFactory = new RuntimeOptionsFactory(clazz);
        runtimeOptions = runtimeOptionsFactory.create();
        resourceLoader = new MultiLoader(classLoader);
        List<CucumberFeature> cfs = runtimeOptions.cucumberFeatures(resourceLoader);
        featureFiles = new ArrayList<>(cfs.size());
        for (CucumberFeature cf : cfs) {
            featureFiles.add(new FeatureFile(cf, new File(cf.getPath())));
        }
    }

    public CucumberRunner(File file) {
        logger.debug("init feature file: {}", file);
        classLoader = Thread.currentThread().getContextClassLoader();
        resourceLoader = new MultiLoader(classLoader);
        RuntimeOptionsFactory runtimeOptionsFactory = new RuntimeOptionsFactory(getClass());
        runtimeOptions = runtimeOptionsFactory.create();
        FeatureWrapper wrapper = FeatureWrapper.fromFile(file, classLoader);
        CucumberFeature feature = wrapper.getFeature();
        FeatureFile featureFile = new FeatureFile(feature, file);
        featureFiles = Collections.singletonList(featureFile);
    }

    public List<CucumberFeature> getFeatures() {
        List<CucumberFeature> list = new ArrayList<>(featureFiles.size());
        for (FeatureFile featureFile : featureFiles) {
            list.add(featureFile.feature);
        }
        return list;
    }

    public List<FeatureFile> getFeatureFiles() {
        return featureFiles;
    }

    public RuntimeOptions getRuntimeOptions() {
        return runtimeOptions;
    }

    public ClassLoader getClassLoader() {
        return classLoader;
    }

    public Runtime getRuntime(CucumberFeature feature) {
        return getRuntime(new FeatureFile(feature, new File(feature.getPath())));
    }

    public Runtime getRuntime(FeatureFile featureFile) {
        File packageFile = featureFile.file;
        String featurePath;
        if (packageFile.exists()) { // loaded by karate
            featurePath = packageFile.getAbsolutePath();
        } else { // was loaded by cucumber-jvm, is relative to classpath
            String temp = packageFile.getPath().replace('\\', '/'); // fix for windows
            featurePath = classLoader.getResource(temp).getFile();
        }
        logger.debug("loading feature: {}", featurePath);
        File featureDir = new File(featurePath).getParentFile();
        ScriptEnv env = new ScriptEnv(false, null, featureDir, packageFile.getName(), classLoader);
        Backend backend = new KarateBackend(env, null, null);
        RuntimeGlue glue = new RuntimeGlue(new UndefinedStepsTracker(), new LocalizedXStreams(classLoader));
        return new Runtime(resourceLoader, classLoader, Collections.singletonList(backend), runtimeOptions, StopWatch.SYSTEM, glue);
    }

    // only called for TestNG ?
    public void finish() {
        Formatter formatter = runtimeOptions.formatter(classLoader);
        formatter.done();
        formatter.close();
    }

    public void run(FeatureFile featureFile, KarateJunitFormatter formatter) {
        Runtime runtime = getRuntime(featureFile);
        featureFile.feature.run(formatter, formatter, runtime);
    }

    public void run(KarateJunitFormatter formatter) {
        for (FeatureFile featureFile : getFeatureFiles()) {
            run(featureFile, formatter);
        }
    }

    private static KarateJunitFormatter getFormatter(String reportDirPath, FeatureFile featureFile) {
        File reportDir = new File(reportDirPath);
        try {
            FileUtils.forceMkdirParent(reportDir);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        String featurePath = featureFile.feature.getPath();
        if (featurePath == null) {
            featurePath = featureFile.file.getPath();
        }
        featurePath = new File(featurePath).getPath(); // fix for windows
        String featurePackagePath = featurePath.replace(File.separator, ".");
        if (featurePackagePath.endsWith(".feature")) {
            featurePackagePath = featurePackagePath.substring(0, featurePackagePath.length() - 8);
        }
        try {
            reportDirPath = reportDir.getPath() + File.separator;
            String reportPath = reportDirPath + "TEST-" + featurePackagePath + ".xml";
            return new KarateJunitFormatter(featurePackagePath, reportPath);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    
    public static KarateStats parallel(Class clazz, int threadCount) {
        return parallel(clazz, threadCount, "target/surefire-reports");
    }

    public static KarateStats parallel(Class clazz, int threadCount, String reportDir) {
        KarateStats stats = KarateStats.startTimer();
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CucumberRunner runner = new CucumberRunner(clazz);
        List<FeatureFile> featureFiles = runner.getFeatureFiles();
        List<Callable<KarateJunitFormatter>> callables = new ArrayList<>(featureFiles.size());
        int count = featureFiles.size();
        for (int i = 0; i < count; i++) {
            int index = i + 1;
            FeatureFile featureFile = featureFiles.get(i);
            callables.add(() -> {
                String threadName = Thread.currentThread().getName();
                KarateJunitFormatter formatter = getFormatter(reportDir, featureFile);
                logger.info(">>>> feature {} of {} on thread {}: {}", index, count, threadName, featureFile.feature.getPath());
                runner.run(featureFile, formatter);
                logger.info("<<<< feature {} of {} on thread {}: {}", index, count, threadName, featureFile.feature.getPath());
                formatter.done();
                return formatter;
            });
        }
        try {
            List<Future<KarateJunitFormatter>> futures = executor.invokeAll(callables);
            stats.stopTimer();
            for (Future<KarateJunitFormatter> future : futures) {
                KarateJunitFormatter formatter = future.get();
                stats.addToTestCount(formatter.getTestCount());
                stats.addToFailCount(formatter.getFailCount());
                stats.addToSkipCount(formatter.getSkipCount());
                stats.addToTimeTaken(formatter.getTimeTaken());
                if (formatter.isFail()) {
                    stats.addToFailedList(formatter.getFeaturePath());
                }
            }
            stats.printStats(threadCount);
            return stats;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
