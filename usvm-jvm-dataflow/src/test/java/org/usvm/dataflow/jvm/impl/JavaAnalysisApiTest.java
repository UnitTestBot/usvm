/*
 *  Copyright 2022 UnitTestBot contributors (utbot.org)
 * <p>
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 * <p>
 *  http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.usvm.dataflow.jvm.impl;

import kotlin.time.DurationUnit;
import org.jacodb.api.jvm.JcClassOrInterface;
import org.jacodb.api.jvm.JcMethod;
import org.jacodb.api.jvm.cfg.JcInst;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.usvm.dataflow.jvm.graph.ApplicationGraphFactory;
import org.usvm.dataflow.jvm.graph.JcApplicationGraph;
import org.usvm.dataflow.jvm.ifds.JcUnitResolver;
import org.usvm.dataflow.jvm.ifds.UnitResolverKt;
import org.usvm.dataflow.jvm.taint.TaintManagerKt;
import org.usvm.dataflow.taint.TaintManager;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

import static kotlin.time.DurationKt.toDuration;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

@TestInstance(PER_CLASS)
public class JavaAnalysisApiTest extends BaseAnalysisTest {
    @Test
    public void testJavaAnalysisApi() throws ExecutionException, InterruptedException {
        JcClassOrInterface analyzedClass = getCp().findClassOrNull("NpeExamples");
        Assertions.assertNotNull(analyzedClass);

        List<JcMethod> methodsToAnalyze = analyzedClass.getDeclaredMethods();
        JcApplicationGraph applicationGraph = ApplicationGraphFactory
                .newApplicationGraphForAnalysisAsync(getCp(), null)
                .get();
        JcUnitResolver unitResolver = UnitResolverKt.getMethodUnitResolver();
        TaintManager<JcMethod, JcInst> manager = TaintManagerKt.jcTaintManager(applicationGraph, unitResolver, false, null);
        manager.analyze(methodsToAnalyze, toDuration(30, DurationUnit.SECONDS));
    }

    @Test
    public void testCustomBannedPackagesApi() throws ExecutionException, InterruptedException {
        List<String> bannedPackages = new ArrayList<>(ApplicationGraphFactory.getDefaultBannedPackagePrefixes());
        bannedPackages.add("my.package.that.wont.be.analyzed");

        JcApplicationGraph customGraph = ApplicationGraphFactory
                .newApplicationGraphForAnalysisAsync(getCp(), bannedPackages)
                .get();
        Assertions.assertNotNull(customGraph);
    }
}
