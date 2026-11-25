<script setup lang="ts">
import { computed } from 'vue'
import { usePdfStore } from '../../stores/pdf'
import CollapsibleSection from '../CollapsibleSection.vue'
import HexValue from '../HexValue.vue'
import { formatBytes, formatDate } from '../../lib/format'
import type { Attribute } from '../../types'

const store = usePdfStore()

const cms = computed(() => store.cms)
const signer = computed(() => store.cms?.signerInfos[0] ?? null)

function attributeTable(attributes: Attribute[] | undefined | null) {
  return attributes ?? []
}
</script>

<template>
  <div class="tab-body">
    <div v-if="store.isBusy('cms')" class="text-muted tab-hint">Loading CMS…</div>

    <div v-else-if="store.errors.some((e) => e.key === 'cms')" class="error-panel">
      {{ store.errors.find((e) => e.key === 'cms')?.message }}
    </div>

    <template v-else-if="cms">
      <CollapsibleSection title="SignedData" default-open>
        <table class="dt-kv">
          <tbody>
            <tr><th>Version</th><td>{{ cms.version }}</td></tr>
            <tr><th>Content type OID</th><td class="mono">{{ cms.contentTypeOid }}</td></tr>
            <tr><th>Content type</th><td>{{ cms.contentTypeName }}</td></tr>
            <tr>
              <th>Digest algorithms</th>
              <td><div v-for="(d, i) in cms.digestAlgorithms" :key="i" class="mono">{{ d }}</div></td>
            </tr>
            <tr><th>SignerInfos</th><td>{{ cms.signerInfos.length }}</td></tr>
            <tr><th>Certificates embedded</th><td>{{ cms.certificates.length }}</td></tr>
            <tr><th>Raw size</th><td>{{ formatBytes(cms.rawSize) }}</td></tr>
          </tbody>
        </table>
      </CollapsibleSection>

      <CollapsibleSection v-if="cms.encapsulatedContentInfo" title="Encapsulated content info" default-open>
        <table class="dt-kv">
          <tbody>
            <tr><th>Content type OID</th><td class="mono">{{ cms.encapsulatedContentInfo.oid }}</td></tr>
            <tr><th>Name</th><td>{{ cms.encapsulatedContentInfo.name }}</td></tr>
            <tr>
              <th>Content</th>
              <td>
                <span v-if="cms.encapsulatedContentInfo.contentLength === null" class="status-badge warn">detached</span>
                <template v-else>
                  {{ formatBytes(cms.encapsulatedContentInfo.contentLength) }}
                  <div v-if="cms.encapsulatedContentInfo.hexPrefix" class="mono text-muted" style="margin-top: 4px">
                    {{ cms.encapsulatedContentInfo.hexPrefix }}
                  </div>
                </template>
              </td>
            </tr>
          </tbody>
        </table>
      </CollapsibleSection>

      <template v-if="signer">
        <CollapsibleSection title="SignerInfo" default-open>
          <table class="dt-kv">
            <tbody>
              <tr><th>Version</th><td>{{ signer.version }}</td></tr>
              <tr><th>Signer identifier type</th><td>{{ signer.signerIdentifierType }}</td></tr>
              <tr><th>Signer identifier</th><td class="mono">{{ signer.signerIdentifier }}</td></tr>
              <tr><th>Digest algorithm</th><td class="mono">{{ signer.digestAlgorithm }}</td></tr>
              <tr><th>Digest algorithm (name)</th><td>{{ signer.digestAlgorithmName }}</td></tr>
              <tr><th>Signature algorithm</th><td class="mono">{{ signer.signatureAlgorithm }}</td></tr>
              <tr><th>Signature algorithm (name)</th><td>{{ signer.signatureAlgorithmName }}</td></tr>
              <tr><th>Signature size</th><td>{{ formatBytes(signer.signatureSize) }}</td></tr>
              <tr>
                <th>Signed attributes</th>
                <td>
                  {{ signer.signedAttributeCount }}
                  <span v-if="signer.hasMessageDigest" class="status-badge ok">message-digest</span>
                  <span v-if="signer.hasSigningTime" class="status-badge ok">signing-time</span>
                </td>
              </tr>
              <tr><th>Unsigned attributes</th><td>{{ signer.unsignedAttributeCount }}</td></tr>
              <tr v-if="signer.signingTime">
                <th>Signing time (CMS)</th>
                <td>{{ formatDate(signer.signingTime) }}</td>
              </tr>
              <tr>
                <th>Message digest</th>
                <td><HexValue :hex="signer.messageDigest" /></td>
              </tr>
            </tbody>
          </table>
        </CollapsibleSection>

        <CollapsibleSection :title="`Signature value (${formatBytes(signer.signatureSize)})`">
          <HexValue :hex="signer.signature" :max-bytes="256" />
        </CollapsibleSection>
      </template>

      <CollapsibleSection title="Signed attributes" default-open>
        <div v-if="attributeTable(cms.signedAttributes).length === 0" class="text-muted" style="padding: 8px 12px">None.</div>
        <div v-for="attr in attributeTable(cms.signedAttributes)" :key="attr.oid" class="attr-block">
          <div class="attr-head">
            <span class="attr-name">{{ attr.name }}</span>
            <span class="mono text-faint">{{ attr.oid }}</span>
          </div>
          <table class="dt-kv">
            <tbody>
              <tr v-for="(value, i) in attr.values" :key="i">
                <th>{{ value.type }}</th>
                <td>
                  <div class="mono attr-display">{{ value.display }}</div>
                  <div v-if="value.hex" class="text-faint mono" style="font-size: 10px">{{ value.hex }}</div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </CollapsibleSection>

      <CollapsibleSection title="Unsigned attributes">
        <div v-if="attributeTable(cms.unsignedAttributes).length === 0" class="text-muted" style="padding: 8px 12px">None.</div>
        <div v-for="attr in attributeTable(cms.unsignedAttributes)" :key="attr.oid" class="attr-block">
          <div class="attr-head">
            <span class="attr-name">{{ attr.name }}</span>
            <span class="mono text-faint">{{ attr.oid }}</span>
          </div>
          <table class="dt-kv">
            <tbody>
              <tr v-for="(value, i) in attr.values" :key="i">
                <th>{{ value.type }}</th>
                <td><div class="mono attr-display">{{ value.display }}</div></td>
              </tr>
            </tbody>
          </table>
        </div>
      </CollapsibleSection>
    </template>
  </div>
</template>

<style scoped>
.tab-body {
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  overflow: auto;
}

.tab-hint {
  padding: 12px;
}

.attr-block {
  padding: 6px 12px;
}

.attr-head {
  display: flex;
  align-items: baseline;
  gap: 10px;
  padding: 6px 0;
}

.attr-name {
  font-weight: 600;
  font-size: 12px;
}

.attr-display {
  white-space: pre-wrap;
  word-break: break-all;
}
</style>
