<script setup lang="ts">
import { computed } from 'vue'
import { usePdfStore } from '../../stores/pdf'
import CollapsibleSection from '../CollapsibleSection.vue'
import { formatBytes, formatDate } from '../../lib/format'
import type { Signature } from '../../types'

const store = usePdfStore()

const signatures = computed(() => store.signatures)
const selectedIndex = computed(() => store.selectedSignatureIndex)

function formatByteRange(byteRange: number[]): string {
  if (!byteRange.length) return '—'
  return byteRange.map((n, i) => `${i % 2 === 0 ? '' : '→ '}${n}`).join(' ')
}

function jumpToPage(signature: Signature) {
  if (signature.page) store.navigateTo(signature.page)
}
</script>

<template>
  <div class="tab-body">
    <div v-if="signatures.length === 0" class="text-muted tab-hint">
      No signatures detected in this document.
    </div>

    <div class="signature-list">
      <CollapsibleSection
        v-for="sig in signatures"
        :key="sig.index"
        :title="sig.signerName || `Signature ${sig.index + 1}`"
        :default-open="sig.index === selectedIndex"
      >
        <template #badges>
          <span v-if="sig.index === selectedIndex" class="selected-tag">selected</span>
        </template>

        <div class="sig-actions">
          <button class="dt-btn" @click="store.selectSignature(sig.index)">
            <i class="pi pi-search" /> Inspect
          </button>
          <button v-if="sig.page" class="dt-btn" @click="jumpToPage(sig)">
            <i class="pi pi-arrow-right" /> Page {{ sig.page }}
          </button>
        </div>

        <table class="dt-kv">
          <tbody>
            <tr><th>Field name</th><td class="mono">{{ sig.fieldName || '—' }}</td></tr>
            <tr><th>Page</th><td>{{ sig.page ?? '—' }}</td></tr>
            <tr v-if="sig.rectangle">
              <th>Rectangle</th>
              <td class="mono">
                x={{ sig.rectangle.x.toFixed(1) }} y={{ sig.rectangle.y.toFixed(1) }}
                w={{ sig.rectangle.width.toFixed(1) }} h={{ sig.rectangle.height.toFixed(1) }}
              </td>
            </tr>
            <tr><th>Reason</th><td>{{ sig.reason || '—' }}</td></tr>
            <tr><th>Location</th><td>{{ sig.location || '—' }}</td></tr>
            <tr><th>Contact info</th><td>{{ sig.contactInfo || '—' }}</td></tr>
            <tr><th>Claimed signing time</th><td>{{ formatDate(sig.signingTime) }}</td></tr>
            <tr><th>Revision</th><td>{{ sig.revision ?? '—' }}</td></tr>
            <tr><th>Filter</th><td class="mono">{{ sig.filter || '—' }}</td></tr>
            <tr><th>SubFilter</th><td class="mono">{{ sig.subFilter || '—' }}</td></tr>
            <tr>
              <th>Byte range</th>
              <td class="mono">{{ formatByteRange(sig.byteRange) }}</td>
            </tr>
            <tr>
              <th>Coverage</th>
              <td>
                {{ formatBytes(sig.byteRangeContentLength) }}
                <span v-if="sig.coversWholeDocument" class="status-badge ok">covers file</span>
                <span v-else class="status-badge warn">partial</span>
              </td>
            </tr>
            <tr><th>CMS size</th><td>{{ formatBytes(sig.contentsSize) }}</td></tr>
          </tbody>
        </table>
      </CollapsibleSection>
    </div>
  </div>
</template>

<style scoped>
.tab-body {
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.signature-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.sig-actions {
  display: flex;
  gap: 8px;
  padding: 8px 12px;
}

.selected-tag {
  color: var(--dt-orange);
  font-size: 11px;
  font-weight: 500;
}
</style>
