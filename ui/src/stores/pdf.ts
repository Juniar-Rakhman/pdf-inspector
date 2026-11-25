import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import {
  analyzePdf,
  loadAsn1,
  loadCertificates,
  loadChains,
  loadCms,
  loadMetadata,
  loadRevisions,
  loadSignatures,
  loadValidation,
  loadWidgets,
} from '../api'
import type {
  Asn1Node,
  Certificate,
  CertificateChain,
  Cms,
  PdfMetadata,
  Revision,
  Signature,
  Validation,
  Widget,
} from '../types'

interface LoadError {
  key: string
  message: string
}

export const usePdfStore = defineStore('pdf', () => {
  const documentId = ref<string | null>(null)
  const metadata = ref<PdfMetadata | null>(null)
  const signatures = ref<Signature[]>([])
  const widgets = ref<Widget[]>([])
  const certificates = ref<Certificate[]>([])
  const chains = ref<CertificateChain[]>([])
  const cms = ref<Cms | null>(null)
  const asn1 = ref<Asn1Node | null>(null)
  const validation = ref<Validation | null>(null)
  const revisions = ref<Revision[]>([])
  const selectedSignatureIndex = ref<number | null>(null)
  const activeRevisionIndex = ref<number | null>(null)
  const navigateRequest = ref<{ token: number; page: number } | null>(null)
  const loading = ref<Record<string, boolean>>({})
  const errors = ref<LoadError[]>([])
  const analyzing = ref(false)

  const selectedSignature = computed(() =>
    selectedSignatureIndex.value === null
      ? null
      : signatures.value.find((s) => s.index === selectedSignatureIndex.value) ?? null,
  )

  const selectedValidation = computed(() =>
    selectedSignatureIndex.value === null
      ? null
      : validation.value?.signatures.find(
          (s) => s.signatureIndex === selectedSignatureIndex.value,
        ) ?? null,
  )

  const selectedChain = computed(() =>
    selectedSignatureIndex.value === null
      ? null
      : chains.value.find((c) => c.signatureIndex === selectedSignatureIndex.value) ?? null,
  )

  const setBusy = (key: string) => {
    loading.value = { ...loading.value, [key]: true }
  }
  const clearBusy = (key: string) => {
    const next = { ...loading.value }
    delete next[key]
    loading.value = next
  }

  const fail = (key: string, message: string) => {
    errors.value = [...errors.value.filter((e) => e.key !== key), { key, message }]
  }

  async function analyze(file: File): Promise<string> {
    analyzing.value = true
    errors.value = []
    try {
      const result = await analyzePdf(file)
      await load(result.documentId)
      return result.documentId
    } finally {
      analyzing.value = false
    }
  }

  async function load(id: string) {
    documentId.value = id
    activeRevisionIndex.value = null
    selectedSignatureIndex.value = null
    const keys = ['metadata', 'signatures', 'widgets', 'certificates', 'chains', 'validation', 'revisions']
    keys.forEach(setBusy)
    try {
      const [md, sigs, wids, certs, chns, val, revs] = await Promise.all([
        loadMetadata(id),
        loadSignatures(id),
        loadWidgets(id),
        loadCertificates(id),
        loadChains(id),
        loadValidation(id),
        loadRevisions(id),
      ])
      metadata.value = md
      signatures.value = sigs
      widgets.value = wids
      certificates.value = certs
      chains.value = chns
      validation.value = val
      revisions.value = revs
      if (sigs.length > 0) {
        selectedSignatureIndex.value = sigs[0].index
      }
    } catch (e) {
      fail('load', e instanceof Error ? e.message : String(e))
    } finally {
      keys.forEach(clearBusy)
    }
  }

  async function loadCmsFor(index: number) {
    if (!documentId.value) return
    setBusy('cms')
    try {
      cms.value = await loadCms(documentId.value, index)
    } catch (e) {
      cms.value = null
      fail('cms', e instanceof Error ? e.message : String(e))
    } finally {
      clearBusy('cms')
    }
  }

  async function loadAsn1For(index: number) {
    if (!documentId.value) return
    setBusy('asn1')
    try {
      asn1.value = await loadAsn1(documentId.value, index)
    } catch (e) {
      asn1.value = null
      fail('asn1', e instanceof Error ? e.message : String(e))
    } finally {
      clearBusy('asn1')
    }
  }

  async function selectSignature(index: number) {
    if (selectedSignatureIndex.value === index && cms.value) return
    selectedSignatureIndex.value = index
    void loadCmsFor(index)
    void loadAsn1For(index)
  }

  function selectRevision(index: number | null) {
    activeRevisionIndex.value = index
  }

  function navigateTo(page: number) {
    navigateRequest.value = { token: (navigateRequest.value?.token ?? 0) + 1, page }
  }

  function clear() {
    documentId.value = null
    metadata.value = null
    signatures.value = []
    widgets.value = []
    certificates.value = []
    chains.value = []
    cms.value = null
    asn1.value = null
    validation.value = null
    revisions.value = []
    selectedSignatureIndex.value = null
    activeRevisionIndex.value = null
    navigateRequest.value = null
    loading.value = {}
    errors.value = []
  }

  const isBusy = (key: string) => Boolean(loading.value[key])

  return {
    documentId,
    metadata,
    signatures,
    widgets,
    certificates,
    chains,
    cms,
    asn1,
    validation,
    revisions,
    selectedSignatureIndex,
    activeRevisionIndex,
    navigateRequest,
    analyzing,
    errors,
    selectedSignature,
    selectedValidation,
    selectedChain,
    isBusy,
    analyze,
    load,
    loadCmsFor,
    loadAsn1For,
    selectSignature,
    selectRevision,
    navigateTo,
    clear,
  }
})
