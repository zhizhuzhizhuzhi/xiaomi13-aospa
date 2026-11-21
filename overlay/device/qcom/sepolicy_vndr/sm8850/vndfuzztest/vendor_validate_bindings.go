// Copyright (C) 2022 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
// Changes from Qualcomm Innovation Center, Inc. are provided under the following license:
// Copyright (c) 2024 Qualcomm Innovation Center, Inc. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause-Clear

package vendor_selinux

import (
	"encoding/json"
	"fmt"

	"android/soong/android"
)

func init() {
	android.RegisterModuleType("vendor_fuzzer_bindings_test", vendorfuzzerBindingsTestFactory)
	android.PreArchMutators(registerFuzzerMutators)
}

func registerFuzzerMutators(ctx android.RegisterMutatorsContext) {
	ctx.BottomUp("addVendorFuzzerConfigDeps", addVendorFuzzerConfigDeps).Parallel()
}

func addVendorFuzzerConfigDeps(ctx android.BottomUpMutatorContext) {
	if _, ok := ctx.Module().(*vendorfuzzerBindingsTestModule); ok {
		for _, fuzzers := range vendorServiceFuzzerBindings {
			for _, fuzzer := range fuzzers {
				if !ctx.OtherModuleExists(fuzzer) && !ctx.Config().AllowMissingDependencies() {
					panic(fmt.Errorf("Fuzzer doesn't exist : %s", fuzzer))
				}
			}
		}
	}
}

type bindingsTestProperties struct {
	// Contexts files to be tested.
	Srcs []string `android:"path"`
}

type vendorfuzzerBindingsTestModule struct {
	android.ModuleBase
	tool          string
	properties    bindingsTestProperties
	testTimestamp android.ModuleOutPath
}

// fuzzer_bindings_test checks if a fuzzer is implemented for every service in service_contexts
func vendorfuzzerBindingsTestFactory() android.Module {
	m := &vendorfuzzerBindingsTestModule{tool: "vendor_fuzzer_bindings_check"}
	m.AddProperties(&m.properties)
	android.InitAndroidArchModule(m, android.DeviceSupported, android.MultilibCommon)
	return m
}

func (m *vendorfuzzerBindingsTestModule) GenerateAndroidBuildActions(ctx android.ModuleContext) {
	tool := m.tool
	if tool != "vendor_fuzzer_bindings_check" {
		panic(fmt.Errorf("%q: unknown tool name: %q", ctx.ModuleName(), tool))
	}
	if len(m.properties.Srcs) == 0 {
		ctx.PropertyErrorf("srcs", "can't be empty")
		panic(fmt.Errorf("Abhishek m.properties.Srcs is comming null %s:", m.properties.Srcs))
		return
	}

	// Generate a json file which contains existing bindings
	rootPath := android.PathForIntermediates(ctx, "vendor_bindings.json")
	jsonString, parseError := json.Marshal(vendorServiceFuzzerBindings)
	if parseError != nil {
		panic(fmt.Errorf("Error while marshalling vendorServiceFuzzerBindings dict. Check Format"))
	}
	android.WriteFileRule(ctx, rootPath, string(jsonString))

	//input module json, service context and binding files here
	srcs := android.PathsForModuleSrc(ctx, m.properties.Srcs)
	rule := android.NewRuleBuilder(pctx, ctx)
	rule.Command().BuiltTool(tool).Flag("-s").Inputs(srcs).Flag("-b").Input(rootPath)

	// Every Soong module needs to generate an output even if it doesn't require it
	m.testTimestamp = android.PathForModuleOut(ctx, "timestamp")
	rule.Command().Text("touch").Output(m.testTimestamp)
	rule.Build("vendor_fuzzer_bindings_test", "running service:fuzzer bindings test: "+ctx.ModuleName())
}

func (m *vendorfuzzerBindingsTestModule) AndroidMkEntries() []android.AndroidMkEntries {
	return []android.AndroidMkEntries{android.AndroidMkEntries{
		Class: "FAKE",
		// OutputFile is needed, even though BUILD_PHONY_PACKAGE doesn't use it.
		// Without OutputFile this module won't be exported to Makefile.
		OutputFile: android.OptionalPathForPath(m.testTimestamp),
		Include:    "$(BUILD_PHONY_PACKAGE)",
		ExtraEntries: []android.AndroidMkExtraEntriesFunc{
			func(ctx android.AndroidMkExtraEntriesContext, entries *android.AndroidMkEntries) {
				entries.SetString("LOCAL_ADDITIONAL_DEPENDENCIES", m.testTimestamp.String())
			},
		},
	}}
}
