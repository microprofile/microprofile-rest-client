/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.eclipse.microprofile.rest.client;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;

import java.io.IOException;
import java.io.InputStream;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleDescriptor.Requires.Modifier;
import java.util.Set;
import java.util.stream.Collectors;

import org.testng.annotations.Test;

/**
 * Checks the explicit module descriptor shipped in the API jar.
 */
public class ModuleDescriptorTest {

    private static ModuleDescriptor descriptor() throws IOException {
        try (InputStream in = ModuleDescriptorTest.class.getResourceAsStream("/module-info.class")) {
            assertNotNull(in, "module-info.class must be present at the root of the API classes");
            return ModuleDescriptor.read(in);
        }
    }

    @Test
    public void testModuleName() throws IOException {
        ModuleDescriptor descriptor = descriptor();
        assertEquals(descriptor.name(), "org.eclipse.microprofile.rest.client");
        assertFalse(descriptor.isOpen(), "the API module must not be an open module");
        assertFalse(descriptor.isAutomatic(), "the API module must be an explicit module");
    }

    @Test
    public void testExportedPackages() throws IOException {
        Set<String> exported = descriptor().exports().stream()
                .map(ModuleDescriptor.Exports::source)
                .collect(Collectors.toSet());
        assertEquals(exported, Set.of(
                "org.eclipse.microprofile.rest.client",
                "org.eclipse.microprofile.rest.client.annotation",
                "org.eclipse.microprofile.rest.client.ext",
                "org.eclipse.microprofile.rest.client.inject",
                "org.eclipse.microprofile.rest.client.spi"));
    }

    @Test
    public void testTransitiveRequires() throws IOException {
        Set<String> transitive = descriptor().requires().stream()
                .filter(r -> r.modifiers().contains(Modifier.TRANSITIVE))
                .map(ModuleDescriptor.Requires::name)
                .collect(Collectors.toSet());
        assertEquals(transitive, Set.of("jakarta.annotation", "jakarta.cdi", "jakarta.inject", "jakarta.ws.rs"));
    }

    @Test
    public void testOptionalRequires() throws IOException {
        Set<String> optional = descriptor().requires().stream()
                .filter(r -> r.modifiers().contains(Modifier.STATIC))
                .map(ModuleDescriptor.Requires::name)
                .collect(Collectors.toSet());
        assertEquals(optional, Set.of("jakarta.cdi", "jakarta.inject", "org.eclipse.microprofile.config"));
    }

    @Test
    public void testMandatoryRequires() throws IOException {
        Set<String> mandatory = descriptor().requires().stream()
                .filter(r -> !r.modifiers().contains(Modifier.STATIC))
                .map(ModuleDescriptor.Requires::name)
                .collect(Collectors.toSet());
        assertEquals(mandatory, Set.of("java.base", "java.logging", "jakarta.annotation", "jakarta.ws.rs"));
    }

    @Test
    public void testServiceLoaderUses() throws IOException {
        assertEquals(descriptor().uses(), Set.of(
                "org.eclipse.microprofile.rest.client.spi.RestClientBuilderListener",
                "org.eclipse.microprofile.rest.client.spi.RestClientBuilderResolver"));
    }
}
