export interface Rectangle {
  x: number
  y: number
  width: number
  height: number
}

export interface Signature {
  index: number
  fieldName: string
  page: number | null
  rectangle: Rectangle | null
  signerName: string
  reason: string
  location: string
  contactInfo: string
  signingTime: string | null
  revision: number | null
  filter: string
  subFilter: string
  byteRange: number[]
  byteRangeContentLength: number
  coversWholeDocument: boolean
  contentsSize: number
}

export interface Widget {
  page: number
  rectangle: Rectangle
  fieldName: string
  signatureIndex: number | null
}

export interface PdfMetadata {
  documentId: string
  filename: string
  fileSize: number
  pageCount: number
  sha256: string
  md5: string
  signatureCount: number
  revisionCount: number
  createdAt: string
  title: string | null
  author: string | null
  producer: string | null
}

export interface UploadResult {
  documentId: string
  filename: string
}

export interface BasicConstraints {
  ca: boolean
  pathLengthConstraint: number | null
}

export interface Certificate {
  index: number
  subject: string
  issuer: string
  serialNumber: string
  version: number
  signatureAlgorithm: string
  publicKeyAlgorithm: string
  publicKeySize: number
  sha1Fingerprint: string
  sha256Fingerprint: string
  validFrom: string
  validUntil: string
  subjectAlternativeNames: string[]
  keyUsage: string[]
  extendedKeyUsage: string[]
  basicConstraints: BasicConstraints | null
  subjectKeyIdentifier: string
  authorityKeyIdentifier: string
  certificatePolicies: string[]
  crlDistributionPoints: string[]
  authorityInformationAccess: string[]
  email: string
  commonName: string
  organization: string
  organizationalUnit: string
  country: string
  selfSigned: boolean
  isCa: boolean
  pem: string
}

export interface CertificateChain {
  signatureIndex: number
  nodes: ChainNode[]
  complete: boolean
  pathLength: number
  missing: boolean
  messages: string[]
}

export interface ChainNode {
  subject: string
  issuer: string
  sha256Fingerprint: string
  type: 'ROOT' | 'INTERMEDIATE' | 'SIGNER'
  selfSigned: boolean
  isCa: boolean
  hasIssuerInDocument: boolean
  certificateIndex: number | null
  depth: number
}

export interface AttributeValue {
  type: string
  display: string
  hex: string
}

export interface Attribute {
  oid: string
  name: string
  values: AttributeValue[]
}

export interface SignerInfo {
  version: number
  signerIdentifierType: string
  signerIdentifier: string
  digestAlgorithm: string
  digestAlgorithmName: string
  signatureAlgorithm: string
  signatureAlgorithmName: string
  signatureSize: number
  signature: string
  signedAttributeCount: number
  unsignedAttributeCount: number
  hasMessageDigest: boolean
  messageDigest: string
  hasSigningTime: boolean
  signingTime: string
}

export interface ContentInfo {
  oid: string
  name: string
  contentLength: number | null
  hexPrefix: string
}

export interface Cms {
  version: number
  contentTypeOid: string
  contentTypeName: string
  digestAlgorithms: string[]
  signerInfos: SignerInfo[]
  signedAttributes: Attribute[]
  unsignedAttributes: Attribute[]
  certificates: Certificate[]
  encapsulatedContentInfo: ContentInfo
  rawSize: number
}

export interface Asn1Node {
  offset: number
  length: number
  headerLength: number
  contentLength: number
  tagClass: string
  tagNumber: number
  tagName: string
  constructed: boolean
  value: string
  hex: string
  children: Asn1Node[]
}

export interface SignatureValidation {
  signatureIndex: number
  signerName: string
  signatureIntegrity: 'VALID' | 'INVALID' | 'UNKNOWN'
  documentIntegrity: 'VALID' | 'INVALID' | 'UNKNOWN'
  coversWholeDocument: boolean
  modifiedAfterSigning: boolean
  messages: string[]
}

export interface CertificateStatus {
  index: number
  subject: string
  status: 'VALID' | 'EXPIRED' | 'NOT_YET_VALID' | 'UNKNOWN'
  validFrom: string
  validUntil: string
  checkTime: string
  messages: string[]
}

export interface ChainStatus {
  index: number
  complete: boolean
  pathLength: number
  messages: string[]
}

export interface TrustStatus {
  index: number
  status: 'TRUSTED' | 'UNKNOWN'
  messages: string[]
}

export interface Validation {
  signatures: SignatureValidation[]
  certificates: CertificateStatus[]
  chains: ChainStatus[]
  trust: TrustStatus[]
  summary: string
  messages: string[]
}

export interface Revision {
  index: number
  startOffset: number
  endOffset: number
  xrefOffset: number
  signatureIndices: number[]
  signerNames: string[]
  description: string
  isLast: boolean
}
