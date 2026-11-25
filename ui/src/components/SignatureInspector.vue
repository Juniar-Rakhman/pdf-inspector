<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import type { Component } from 'vue'
import { usePdfStore } from '../stores/pdf'
import OverviewTab from './inspector/OverviewTab.vue'
import SignaturesTab from './inspector/SignaturesTab.vue'
import ChainTab from './inspector/ChainTab.vue'
import CmsTab from './inspector/CmsTab.vue'
import Asn1Tab from './inspector/Asn1Tab.vue'
import RevisionsTab from './inspector/RevisionsTab.vue'
import ValidationTab from './inspector/ValidationTab.vue'
import RawJsonTab from './inspector/RawJsonTab.vue'

const store = usePdfStore()

const tabs = [
  { key: 'overview', label: 'Overview', icon: 'pi pi-home' },
  { key: 'signatures', label: 'Signatures', icon: 'pi pi-pen' },
  { key: 'chain', label: 'Certificate Chain', icon: 'pi pi-sitemap' },
  { key: 'cms', label: 'CMS / PKCS#7', icon: 'pi pi-file-o' },
  { key: 'asn1', label: 'ASN.1 Explorer', icon: 'pi pi-sort-amount-down-alt' },
  { key: 'revisions', label: 'Incremental Revisions', icon: 'pi pi-history' },
  { key: 'validation', label: 'Validation', icon: 'pi pi-check-circle' },
  { key: 'json', label: 'Raw JSON', icon: 'pi pi-braces' },
] as const

type TabKey = (typeof tabs)[number]['key']

const activeTab = ref<TabKey>('overview')

const activeComponent = computed(() => {
  const map: Record<TabKey, Component> = {
    overview: OverviewTab,
    signatures: SignaturesTab,
    chain: ChainTab,
    cms: CmsTab,
    asn1: Asn1Tab,
    revisions: RevisionsTab,
    validation: ValidationTab,
    json: RawJsonTab,
  }
  return map[activeTab.value]
})

function onTabSwitch(key: TabKey) {
  if (key === 'cms') void store.loadCmsFor(store.selectedSignatureIndex ?? 0)
  if (key === 'asn1') void store.loadAsn1For(store.selectedSignatureIndex ?? 0)
}

onMounted(() => onTabSwitch(activeTab.value))

watch(
  () => store.selectedSignatureIndex,
  () => {
    if (activeTab.value === 'cms') void store.loadCmsFor(store.selectedSignatureIndex ?? 0)
    if (activeTab.value === 'asn1') void store.loadAsn1For(store.selectedSignatureIndex ?? 0)
  },
)
</script>

<template>
  <div class="inspector">
    <div class="dt-tabs">
      <div
        v-for="tab in tabs"
        :key="tab.key"
        class="dt-tab"
        :class="{ active: activeTab === tab.key }"
        @click="activeTab = tab.key; onTabSwitch(tab.key)"
      >
        <i :class="tab.icon" style="font-size: 12px" />
        {{ tab.label }}
      </div>
    </div>
    <div class="tab-content">
      <component :is="activeComponent" :key="activeTab" />
    </div>
  </div>
</template>

<style scoped>
.inspector {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-width: 0;
}

.tab-content {
  flex: 1;
  min-height: 0;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.tab-content > :deep(*) {
  flex: 1;
  min-height: 0;
}
</style>
