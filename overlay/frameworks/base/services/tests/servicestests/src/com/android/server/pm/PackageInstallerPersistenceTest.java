/*
 * Copyright (C) 2026 The Paranoid Android Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.server.pm;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import android.util.AtomicFile;
import android.util.SparseArray;
import android.util.SparseIntArray;
import com.android.modules.utils.TypedXmlSerializer;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class PackageInstallerPersistenceTest {
    @Rule public final TemporaryFolder mTemp = new TemporaryFolder();

    private static void field(Object target, String name, Object value) throws Exception {
        Field f = PackageInstallerService.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    @Test public void synchronousAndHandlerWritesCannotOverlap() throws Exception {
        PackageInstallerService service = mock(PackageInstallerService.class, CALLS_REAL_METHODS);
        File target = new File(mTemp.getRoot(), "install_sessions.xml");
        field(service, "mSessionsFileLock", new Object());
        field(service, "mSessionsFile", new AtomicFile(target));
        field(service, "mSessionsDir", mTemp.getRoot());
        field(service, "mDeveloperVerificationPolicyPerUser", new SparseIntArray());
        SparseArray<PackageInstallerSession> sessions = new SparseArray<>();
        PackageInstallerSession session = mock(PackageInstallerSession.class);
        sessions.put(42, session);
        field(service, "mSessions", sessions);
        CountDownLatch firstEntered = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondEntered = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        AtomicInteger writers = new AtomicInteger();
        AtomicInteger maximum = new AtomicInteger();
        doAnswer(invocation -> {
            maximum.accumulateAndGet(writers.incrementAndGet(), Math::max);
            try {
                if (calls.incrementAndGet() == 1) {
                    firstEntered.countDown();
                    assertTrue(releaseFirst.await(10, TimeUnit.SECONDS));
                } else {
                    secondEntered.countDown();
                }
                TypedXmlSerializer xml = invocation.getArgument(0);
                xml.startTag(null, "session");
                xml.attributeInt(null, "sessionId", 42);
                xml.endTag(null, "session");
            } finally {
                writers.decrementAndGet();
            }
            return null;
        }).when(session).write(any(), any());
        Method write = PackageInstallerService.class.getDeclaredMethod("writeSessions");
        write.setAccessible(true);
        var threads = Executors.newFixedThreadPool(2);
        try {
            var first = threads.submit(() -> (Boolean) write.invoke(service));
            assertTrue(firstEntered.await(10, TimeUnit.SECONDS));
            var second = threads.submit(() -> {
                secondStarted.countDown();
                return (Boolean) write.invoke(service);
            });
            assertTrue(secondStarted.await(10, TimeUnit.SECONDS));
            assertFalse(secondEntered.await(150, TimeUnit.MILLISECONDS));
            releaseFirst.countDown();
            assertTrue(first.get(10, TimeUnit.SECONDS));
            assertTrue(second.get(10, TimeUnit.SECONDS));
            assertEquals(1, maximum.get());
            assertEquals(2, calls.get());
            assertTrue(Files.size(target.toPath()) > 0);
            assertFalse(new File(target.getPath() + ".new").exists());
        } finally {
            releaseFirst.countDown();
            threads.shutdownNow();
        }
    }
}
