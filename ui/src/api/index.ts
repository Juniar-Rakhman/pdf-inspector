import type {
  Asn1Node,
  Certificate,
  CertificateChain,
  Cms,
  PdfMetadata,
  Revision,
  Signature,
  UploadResult,
  Validation,
  Widget,
} from '../types'

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(path, init)
  if (!res.ok) {
    let message = `Request failed: ${res.status} ${res.statusText}`
    try {
      const body = (await res.json()) as { message?: string; details?: string[] }
      if (body.message) {
        message = body.message
        if (body.details?.length) {
          message = `${message}\n${body.details.join('\n')}`
        }
      }
    } catch {
      // non-JSON error body; keep the default message
    }
    throw new Error(message)
  }
  return res.json() as Promise<T>
}

export async function analyzePdf(file: File): Promise<UploadResult> {
  const formData = new FormData()
  formData.append('file', file)
  return request<UploadResult>('/api/pdf/analyze', { method: 'POST', body: formData })
}

export function metadataUrl(documentId: string): string {
  return `/api/pdf/${documentId}`
}

export function contentUrl(documentId: string): string {
  return `/api/pdf/${documentId}/content`
}

export function revisionContentUrl(documentId: string, revisionIndex: number): string {
  return `/api/pdf/${documentId}/revisions/${revisionIndex}/content`
}

export const loadMetadata = (id: string) => request<PdfMetadata>(metadataUrl(id))
export const loadSignatures = (id: string) => request<Signature[]>(`/api/pdf/${id}/signatures`)
export const loadWidgets = (id: string) => request<Widget[]>(`/api/pdf/${id}/widgets`)
export const loadCertificates = (id: string) => request<Certificate[]>(`/api/pdf/${id}/certificates`)
export const loadChains = (id: string) => request<CertificateChain[]>(`/api/pdf/${id}/certificates/chains`)
export const loadCms = (id: string, signatureIndex: number) =>
  request<Cms>(`/api/pdf/${id}/cms/${signatureIndex}`)
export const loadAsn1 = (id: string, signatureIndex: number) =>
  request<Asn1Node>(`/api/pdf/${id}/asn1/${signatureIndex}`)
export const loadValidation = (id: string) => request<Validation>(`/api/pdf/${id}/validation`)
export const loadRevisions = (id: string) => request<Revision[]>(`/api/pdf/${id}/revisions`)
