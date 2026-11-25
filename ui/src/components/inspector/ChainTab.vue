<script setup lang="ts">
import { computed, ref } from 'vue'
import { usePdfStore } from '../../stores/pdf'
import StatusBadge from '../StatusBadge.vue'
import CollapsibleSection from '../CollapsibleSection.vue'
import { formatDate } from '../../lib/format'
import type { ChainNode } from '../../types'

const store = usePdfStore()

const selectedChain = computed(() => store.selectedChain)
const certificates = computed(() => store.certificates)
const expandedCertIndex = ref<number | null>(null)

function toggleCert(index: number) {
  expandedCertIndex.value = expandedCertIndex.value === index ? null : index
}

function nodeTypeLabel(node: ChainNode): string {
  switch (node.type) {
    case 'ROOT':
      return 'Root'
    case 'INTERMEDIATE':
      return 'Intermediate'
    default:
      return 'Signer'
  }
}

function compactDn(dn: string): string {
  return dn
    .split(',')
    .map((p) => p.trim())
    .filter((p) => p.startsWith('CN=') || p.startsWith('O='))
    .join(', ')
}
</script>

<template>
  <div class="tab-body">
    <div v-if="!store.isBusy('chains') && !selectedChain" class="text-muted tab-hint">
      Select a signature to inspect its certificate chain.
    </div>

    <template v-if="selectedChain">
      <div class="chain-status">
        <StatusBadge :status="selectedChain.complete ? 'COMPLETE' : 'INCOMPLETE'" />
        <span class="text-muted">Path length: {{ selectedChain.pathLength }}</span>
        <span v-if="selectedChain.missing" class="status-badge warn">missing certificates</span>
      </div>
      <ul v-if="selectedChain.messages.length" class="messages">
        <li v-for="(m, i) in selectedChain.messages" :key="i">{{ m }}</li>
      </ul>

      <div class="chain-viz">
        <div v-for="(node, i) in selectedChain.nodes" :key="i" class="chain-node">
          <div class="chain-node-card" :class="node.type.toLowerCase()">
            <div class="chain-node-type">{{ nodeTypeLabel(node) }}</div>
            <div class="chain-node-subject" :title="node.subject">{{ compactDn(node.subject) || node.subject }}</div>
            <div class="text-faint mono" style="font-size: 10px">{{ node.sha256Fingerprint.slice(0, 32) }}…</div>
            <div class="chain-node-flags">
              <span v-if="node.selfSigned" class="status-badge ok">self-signed</span>
              <span v-if="node.isCa" class="status-badge unknown">CA</span>
              <span v-if="!node.hasIssuerInDocument" class="status-badge warn">issuer not embedded</span>
            </div>
          </div>
          <div v-if="i < selectedChain.nodes.length - 1" class="chain-arrow">
            <i class="pi pi-angle-down" />
          </div>
        </div>
        <div v-if="selectedChain.nodes.length === 0" class="text-muted">No chain could be built.</div>
      </div>
    </template>

    <CollapsibleSection
      v-for="cert in certificates"
      :key="cert.index"
      :title="`Certificate ${cert.index + 1} — ${cert.commonName || cert.subject}`"
      :default-open="expandedCertIndex === cert.index"
    >
      <template #badges>
        <span v-if="cert.selfSigned" class="status-badge ok">self-signed</span>
        <span v-if="cert.isCa" class="status-badge unknown">CA</span>
        <span class="status-badge unknown">{{ cert.publicKeyAlgorithm }} {{ cert.publicKeySize }}</span>
      </template>
      <button class="dt-btn cert-toggle" style="margin: 8px 12px" @click="toggleCert(cert.index)">
        <i class="pi pi-list" /> Details
      </button>

      <div v-show="expandedCertIndex === cert.index" class="cert-details">
        <table class="dt-kv">
          <tbody>
            <tr><th>Subject</th><td class="mono">{{ cert.subject }}</td></tr>
            <tr><th>Issuer</th><td class="mono">{{ cert.issuer }}</td></tr>
            <tr><th>Serial number</th><td class="mono">{{ cert.serialNumber }}</td></tr>
            <tr><th>Version</th><td>v{{ cert.version }}</td></tr>
            <tr><th>Signature algorithm</th><td>{{ cert.signatureAlgorithm }}</td></tr>
            <tr><th>Public key</th><td>{{ cert.publicKeyAlgorithm }} ({{ cert.publicKeySize }} bits)</td></tr>
            <tr><th>SHA-1 fingerprint</th><td class="mono">{{ cert.sha1Fingerprint }}</td></tr>
            <tr><th>SHA-256 fingerprint</th><td class="mono">{{ cert.sha256Fingerprint }}</td></tr>
            <tr><th>Valid from</th><td>{{ formatDate(cert.validFrom) }}</td></tr>
            <tr><th>Valid until</th><td>{{ formatDate(cert.validUntil) }}</td></tr>
            <tr><th>Email</th><td>{{ cert.email || '—' }}</td></tr>
            <tr><th>Organization</th><td>{{ cert.organization || '—' }}</td></tr>
            <tr><th>Organizational unit</th><td>{{ cert.organizationalUnit || '—' }}</td></tr>
            <tr><th>Country</th><td>{{ cert.country || '—' }}</td></tr>
            <tr><th>Subject key id</th><td class="mono">{{ cert.subjectKeyIdentifier || '—' }}</td></tr>
            <tr><th>Authority key id</th><td class="mono">{{ cert.authorityKeyIdentifier || '—' }}</td></tr>
            <tr v-if="cert.subjectAlternativeNames.length">
              <th>Subject alt names</th>
              <td><div v-for="(san, i) in cert.subjectAlternativeNames" :key="i" class="mono">{{ san }}</div></td>
            </tr>
            <tr v-if="cert.keyUsage.length">
              <th>Key usage</th>
              <td>{{ cert.keyUsage.join(', ') }}</td>
            </tr>
            <tr v-if="cert.extendedKeyUsage.length">
              <th>Extended key usage</th>
              <td><div v-for="(eku, i) in cert.extendedKeyUsage" :key="i" class="mono">{{ eku }}</div></td>
            </tr>
            <tr v-if="cert.basicConstraints">
              <th>Basic constraints</th>
              <td>
                CA={{ cert.basicConstraints.ca }}
                <template v-if="cert.basicConstraints.pathLengthConstraint !== null">
                  , path length={{ cert.basicConstraints.pathLengthConstraint }}
                </template>
              </td>
            </tr>
            <tr v-if="cert.certificatePolicies.length">
              <th>Certificate policies</th>
              <td><div v-for="(p, i) in cert.certificatePolicies" :key="i" class="mono">{{ p }}</div></td>
            </tr>
            <tr v-if="cert.crlDistributionPoints.length">
              <th>CRL distribution points</th>
              <td><div v-for="(c, i) in cert.crlDistributionPoints" :key="i" class="mono">{{ c }}</div></td>
            </tr>
            <tr v-if="cert.authorityInformationAccess.length">
              <th>Authority info access</th>
              <td><div v-for="(a, i) in cert.authorityInformationAccess" :key="i" class="mono">{{ a }}</div></td>
            </tr>
            <tr><th>PEM</th><td><pre class="pem mono">{{ cert.pem }}</pre></td></tr>
          </tbody>
        </table>
      </div>
    </CollapsibleSection>
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

.chain-status {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.messages {
  list-style: none;
  padding: 0 2px;
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 12px;
  color: var(--dt-text-muted);
}

.messages li::before {
  content: '• ';
  color: var(--dt-text-faint);
}

.chain-viz {
  display: flex;
  flex-direction: column;
  align-items: stretch;
}

.chain-node-card {
  border: 1px solid var(--dt-border-strong);
  border-radius: 6px;
  padding: 8px 12px;
  background: var(--dt-bg-panel);
}

.chain-node-card.root {
  border-left: 3px solid var(--dt-purple);
}

.chain-node-card.intermediate {
  border-left: 3px solid var(--dt-blue);
}

.chain-node-card.signer {
  border-left: 3px solid var(--dt-green);
}

.chain-node-type {
  font-size: 10px;
  text-transform: uppercase;
  letter-spacing: 0.08em;
  color: var(--dt-text-faint);
}

.chain-node-subject {
  font-size: 12px;
  font-weight: 600;
  margin: 2px 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.chain-node-flags {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
  margin-top: 4px;
}

.chain-arrow {
  text-align: center;
  color: var(--dt-text-faint);
  padding: 2px 0;
}

.cert-details {
  padding: 0 12px 8px;
}

.pem {
  max-height: 180px;
  overflow: auto;
  white-space: pre;
  font-size: 10px;
  color: var(--dt-text-muted);
}
</style>
