/*
  * Copyright (c) 2025 Qualcomm Innovation Center, Inc. All rights reserved.
  * SPDX-License-Identifier: BSD-3-Clause-Clear
*/

#include "QspaInterface.h"
#include <iostream>
#include <stdlib.h>
#include <getopt.h>
#include <cutils/properties.h>

int32_t cpus = 0;
int32_t clusters = 0;
vector<string> parts;
vector<int32_t> subpart_info;
vector<int32_t> cpu_ph_clusters;
vector<PartInfo> partsInfo;
int ret = 0;
static std::shared_ptr<IQspa> qspaInterface= nullptr;

extern "C"
{
    QspaWrapper *createInstance()
    {
       if (!qspaInterface) {
          getServiceHandle();
       }
       if (!wrapperInstance) {
          wrapperInstance = new QspaInterface;
       }
       return wrapperInstance;
    }

    void destroyInstance(QspaWrapper *wrapperInstance)
    {
      if (wrapperInstance) {
         delete wrapperInstance;
      }
    }
}

int QspaInterface::getallsubparts(std::vector<struct partInfo>* partinfo)
{
    struct partInfo info_t;
    if (qspaInterface == nullptr) {
        getServiceHandle();
    }
    if (qspaInterface != nullptr) {
        auto status = qspaInterface->get_all_subparts(&partsInfo);
        if (status.isOk()) {
            for (const auto& info : partsInfo) {
                //  partinfo->push_back(info);
                info_t.part = info.part.c_str();
                info_t.subpart_data = info.subpart_data;
                ALOGI("Part: %s",info.part.c_str());
                for (int data : info.subpart_data) {
                    ALOGI("%d", data);
                }
                partinfo->push_back(info_t);
            }
        } else {
            string log = "get_all_subparts() FAILED with reason: " + status.getDescription();
            ALOGE(log.c_str());
            qspaInterface = nullptr;
            ret = -1;
        }
    }
    return ret;
}

int QspaInterface::getavailableparts(vector<string> *avparts)
{
    if (qspaInterface == nullptr) {
        getServiceHandle();
    }
    if (qspaInterface != nullptr) {
        auto status = qspaInterface->get_available_parts(&parts);
        if (status.isOk()) {
            ALOGI("printing parts");
            for (const std::string& word:parts) {
                avparts->push_back(word);
                ALOGI("%s",word.c_str());
            }
        } else {
            string log = "get_available_parts() FAILED with reason: " + status.getDescription();
            ALOGE(log.c_str());
            qspaInterface = nullptr;
            ret = -1;
        }
    }
    return ret;
}

int QspaInterface::getsubpartinfo(const std::string in_part,vector<int32_t> *info)
{
    if (qspaInterface == nullptr) {
        getServiceHandle();
    }
    if (qspaInterface != nullptr) {
        auto status = qspaInterface->get_subpart_info(in_part,&subpart_info);
        if (status.isOk()) {
            for (int num:subpart_info) {
                info->push_back(num);
                ALOGI("%d", num);
            }
        } else {
            string log = "get_subpart_info(" + in_part + ") FAILED with reason: " + status.getDescription();
            ALOGE(log.c_str());
            qspaInterface = nullptr;
            ret = -1;
        }
    }
    return ret;
}

int QspaInterface::getnumavailablecpus(int *num)
{
    if (qspaInterface == nullptr) {
        getServiceHandle();
    }
    if (qspaInterface != nullptr) {
        auto status = qspaInterface->get_num_available_cpus(&cpus);
        if (status.isOk()) {
            *num = cpus;
            ALOGI("Number of available cpus: %d", cpus);
        } else {
            string log = "get_num_available_cpus() FAILED with reason: " + status.getDescription();
            ALOGE(log.c_str());
            qspaInterface = nullptr;
            ret = -1;
        }
    }
    return ret;
}

int QspaInterface::getnumavailableclusters(int *num)
{
   if (qspaInterface == nullptr) {
       getServiceHandle();
   }
   if (qspaInterface != nullptr) {
        auto status = qspaInterface->get_num_available_clusters(&clusters);
        if (status.isOk()) {
            *num = clusters;
            ALOGI("Number of available clusters: %d", clusters);
        } else {
            string log = "get_num_available_clusters() FAILED with reason: " + status.getDescription();
            ALOGE(log.c_str());
            qspaInterface = nullptr;
            ret = -1;
        }
   }
    return ret;
}

int QspaInterface::getnumphysicalclusters(int *num)
{
    if (qspaInterface == nullptr) {
       getServiceHandle();
    }
    if (qspaInterface != nullptr) {
        auto status = qspaInterface->get_num_physical_clusters(&clusters);
        if (status.isOk()) {
            *num = clusters;
            ALOGI("Number of physical clusters: %d", clusters);
        } else {
            string log = "get_num_physical_clusters() FAILED with reason: " + status.getDescription();
            ALOGE(log.c_str());
            qspaInterface = nullptr;
            ret = -1;
        }
    }
    return ret;
}

int QspaInterface::getcpusofphysicalclusters(int32_t phy_cluster_num,vector<int32_t> *info)
{
    if (qspaInterface == nullptr) {
       getServiceHandle();
    }
    if (qspaInterface != nullptr) {
        auto status = qspaInterface->get_cpus_of_physical_clusters(phy_cluster_num,&cpu_ph_clusters);
        if (status.isOk()) {
            ALOGI("Printing cpus");
            for (int num:cpu_ph_clusters) {
                info->push_back(num);
                ALOGI("%d", num);
            }
        } else {
            string log = "get_cpus_of_physical_clusters(" + to_string(phy_cluster_num) + ") FAILED";
            log += "with reason: " + status.getDescription();
            ALOGE(log.c_str());
            qspaInterface = nullptr;
            ret = -1;
        }
    }
    return ret;
}
int QspaInterface::getSocFamilyName(string *info)
{
    char value[92] = {};
    if (property_get("ro.vendor.board.family", value, "") <= 0) {
        ALOGE("get_soc_family_name FAILED with reason: Unable to fetch the property ro.vendor.board.family");
        ret = -1;
    }
    else {
        *info = value;
        ALOGI("SocFamilyName is %s",(*info).c_str());
    }
    return ret;
}

int QspaInterface::getSocName(string *info)
{
    char value[92] = {};
    if (property_get("ro.vendor.qti.soc_name", value, "") <= 0) {
        ALOGE("get_soc_name FAILED with reason: Unable to fetch the property ro.vendor.qti.soc_name");
        ret = -1;
    }
    else {
        *info = value;
        ALOGI("SocName is %s",(*info).c_str());
    }
    return ret;
}

int QspaInterface::getSocId(int *info)
{
    char value[92] = {};
    if (property_get("ro.vendor.qti.soc_id", value, "") <= 0) {
        ALOGE("get_soc_id FAILED with reason: Unable to fetch the property ro.vendor.qti.soc_id");
        ret = -1;
    }
    else {
        *info = stoi(value);
        ALOGI("SocId is %d",(*info));
    }
    return ret;
}

void getServiceHandle() {
     static const std::string instance = std::string() + IQspa::descriptor +"/default";
     ndk::SpAIBinder binder(AServiceManager_waitForService(instance.c_str()));
     qspaInterface = IQspa::fromBinder(binder);
     if (qspaInterface == nullptr) {
        string log = "Failed to get Qspa service handler";
        ALOGE(log.c_str());
     }
}
