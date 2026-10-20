/*
 * Copyright (C) 2026 The Paranoid Android Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.systemui.statusbar.pipeline.ims.data.repository

import com.android.systemui.kosmos.Kosmos
import com.android.systemui.statusbar.pipeline.ims.data.model.ImsIconModel
import com.android.systemui.statusbar.pipeline.ims.data.model.ImsStateModel
import kotlinx.coroutines.flow.MutableStateFlow

val Kosmos.commonImsRepository by Kosmos.Fixture { FakeCommonImsRepository() }

class FakeCommonImsRepository : CommonImsRepository {
    override val imsStates = MutableStateFlow<List<ImsStateModel>>(emptyList())
    override val imsIconState = MutableStateFlow(ImsIconModel())
    private val repositories = mutableMapOf<Int, ImsRepository>()

    override fun getRepoForSubId(subId: Int): ImsRepository =
        repositories.getOrPut(subId) { FakeSubscriptionImsRepository(subId) }

    private class FakeSubscriptionImsRepository(override val subId: Int) : ImsRepository {
        override val imsState = MutableStateFlow(ImsStateModel(subId = subId))
    }
}

/** Factory seam for tests exercising the real full/mobile repository constructors. */
fun fakeImsRepositoryFactory(): ImsRepositoryImpl.Factory {
    val repositories = FakeCommonImsRepository()
    val factory = org.mockito.kotlin.mock<ImsRepositoryImpl.Factory>()
    org.mockito.kotlin.whenever(factory.build(org.mockito.kotlin.any())).thenAnswer {
        repositories.getRepoForSubId(it.getArgument<Int>(0))
    }
    return factory
}
