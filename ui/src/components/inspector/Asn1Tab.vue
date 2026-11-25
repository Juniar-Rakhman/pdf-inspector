<script setup lang="ts">
import { ref, watch } from 'vue'
import { usePdfStore } from '../../stores/pdf'
import Asn1Tree from '../Asn1Tree.vue'

const store = usePdfStore()

const treeKey = ref(0)
const defaultExpanded = ref(true)

function setAll(expanded: boolean) {
  defaultExpanded.value = expanded
  treeKey.value++
}

watch(
  () => store.selectedSignatureIndex,
  () => {
    defaultExpanded.value = true
    treeKey.value++
  },
)
</script>

<template>
  <div class="asn1-tab">
    <div class="asn1-toolbar">
      <span class="text-muted">DER parse of the CMS for the selected signature</span>
      <span class="toolbar-spacer" />
      <button class="dt-btn" @click="setAll(true)"><i class="pi pi-plus" /> Expand all</button>
      <button class="dt-btn" @click="setAll(false)"><i class="pi pi-minus" /> Collapse all</button>
    </div>

    <div class="asn1-scroll">
      <div v-if="store.isBusy('asn1')" class="text-muted" style="padding: 12px">Loading ASN.1…</div>
      <div v-else-if="store.errors.some((e) => e.key === 'asn1')" class="error-panel">
        {{ store.errors.find((e) => e.key === 'asn1')?.message }}
      </div>
      <template v-else-if="store.asn1">
        <Asn1Tree :key="treeKey" :node="store.asn1" :depth="0" :default-expanded="defaultExpanded" />
      </template>
      <div v-else class="text-muted" style="padding: 12px">No ASN.1 data loaded.</div>
    </div>
  </div>
</template>

<style scoped>
.asn1-tab {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.asn1-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border-bottom: 1px solid var(--dt-border);
  flex-shrink: 0;
}

.asn1-scroll {
  flex: 1;
  overflow: auto;
  padding: 6px 0 12px;
}
</style>
