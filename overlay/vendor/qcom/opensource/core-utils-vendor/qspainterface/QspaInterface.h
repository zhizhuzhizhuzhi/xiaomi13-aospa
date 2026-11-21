/*
  * Copyright (c) 2025 Qualcomm Innovation Center, Inc. All rights reserved.
  * SPDX-License-Identifier: BSD-3-Clause-Clear
*/

#include <android/binder_manager.h>
#include <android/binder_process.h>
#include <android/binder_status.h>
#include <aidl/vendor/qti/hardware/qspa/IQspa.h>
#include <string>
#include <vector>
#include <log/log.h>
#include "QspaClient.h"

using aidl::vendor::qti::hardware::qspa::IQspa;
using aidl::vendor::qti::hardware::qspa::PartInfo;

using namespace std;

class QspaInterface : public QspaWrapper
{
      public:
        int getallsubparts(std::vector<struct partInfo>* partinfo) override;
        int getavailableparts(vector<std::string> *ret) override;
        int getsubpartinfo(const std::string in_part,vector<int32_t> *info) override;
        int getnumavailablecpus(int *n) override;
        int getnumavailableclusters(int *n) override;
        int getnumphysicalclusters(int *n) override;
        int getcpusofphysicalclusters(int32_t in_physical_cluster_number,vector<int32_t> *info) override;
        int getSocFamilyName(std::string *info) override;
        int getSocName(std::string *info) override;
        int getSocId(int *id) override;
};

static QspaWrapper* wrapperInstance = nullptr;
extern "C" {
    QspaWrapper* createInstance();
    void destroyInstance(QspaWrapper* wrapperInstance);
}
void  getServiceHandle();
