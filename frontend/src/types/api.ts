export type BackendRole = 'ADMIN' | 'TECNICO' | 'REVISOR' | 'CLIENTE'
export type ManualStatus = 'DRAFT' | 'REVIEW' | 'APPROVED' | 'PUBLISHED' | 'ARCHIVED'
export type LanguageCode = 'ES' | 'EN'
export type BlockType =
  | 'HEADING'
  | 'PARAGRAPH'
  | 'ORDERED_LIST'
  | 'UNORDERED_LIST'
  | 'TABLE'
  | 'IMAGE'
  | 'CHART'
  | 'FORMULA'
  | 'NOTE'
  | 'WARNING'
  | 'PAGE_BREAK'

export type AssetType =
  | 'IMAGE'
  | 'PRODUCT_IMAGE'
  | 'LOGO'
  | 'DOCUMENT_SOURCE'
  | 'PDF_EXPORT'
  | 'EXTRACTED_IMAGE'
  | 'TEMPLATE_RESOURCE'
  | 'OTHER'

export interface LoginRequest {
  email: string
  password: string
}

export interface LoginResponse {
  userId: number
  fullName: string
  email: string
  role: BackendRole
  token: string
}

export interface DashboardResponse {
  products: number
  manuals: number
  publishedManuals: number
  draftManuals: number
  reviewManuals: number
  manualsWithEnglishPending: number
}

export interface ProductResponse {
  id: number
  code: string
  name: string
  family?: string
  category?: string
  description?: string
  active: boolean
  createdAt?: string
  updatedAt?: string
}

export interface ProductRequest {
  code: string
  name: string
  family?: string
  category?: string
  description?: string
  active?: boolean
}

export interface DocumentTypeResponse {
  id: number
  code: string
  name: string
  description?: string
  active: boolean
  sortOrder: number
}

export interface ManualSummaryResponse {
  id: number
  code: string
  title: string
  titleEs?: string
  titleEn?: string
  category?: string
  enabled?: boolean
  documentTypeId?: number
  documentTypeCode?: string
  documentTypeName?: string
  documentYear?: string
  documentVersion?: string
  languageCode?: string
  productId: number
  productCode: string
  productName: string
  activeVersionId?: number
  activeVersionNumber?: string
  activeStatus?: ManualStatus
  esReady: boolean
  enReady: boolean
  updatedAt?: string
  deletedAt?: string
}

export interface ManualDetailResponse {
  id: number
  code: string
  title: string
  titleEs?: string
  titleEn?: string
  category?: string
  enabled?: boolean
  documentTypeId?: number
  documentTypeCode?: string
  documentTypeName?: string
  documentYear?: string
  documentVersion?: string
  languageCode?: string
  productId: number
  productCode: string
  productName: string
  createdAt?: string
  updatedAt?: string
  deletedAt?: string
  activeVersion?: ManualVersionResponse
  versions: ManualVersionResponse[]
}

export interface ManualVersionResponse {
  id: number
  versionNumber: string
  status: ManualStatus
  active: boolean
  esReady: boolean
  enReady: boolean
  changeNotes?: string
  createdAt?: string
  updatedAt?: string
  publishedAt?: string
  sections: ManualSectionResponse[]
}

export interface ManualSectionResponse {
  id: number
  sortOrder: number
  sectionNumber?: string
  titleEs: string
  titleEn?: string
  completionStatus?: string
  blocks: ManualBlockResponse[]
}

export interface ManualBlockResponse {
  id: number
  sortOrder: number
  blockType: BlockType
  languageCode: LanguageCode
  contentJson: string
}

export interface ManualCreateRequest {
  code?: string
  title: string
  titleEs?: string
  titleEn?: string
  category?: string
  productId: number
  documentTypeId?: number
  documentYear?: string
  documentVersion?: string
  languageCode?: string
}

export interface ManualVersionRequest {
  versionNumber: string
  status: ManualStatus
  active: boolean
  esReady: boolean
  enReady: boolean
  changeNotes?: string
  sections: ManualSectionRequest[]
}

export interface ManualSectionRequest {
  id?: number
  sortOrder: number
  sectionNumber?: string
  titleEs: string
  titleEn?: string
  completionStatus?: string
  blocks: ManualBlockRequest[]
}

export interface ManualBlockRequest {
  id?: number
  sortOrder: number
  blockType: BlockType
  languageCode: LanguageCode
  contentJson: string
}

export interface AssetResponse {
  id: number
  originalFilename: string
  storedFilename: string
  mimeType?: string
  fileSize?: number
  storagePath: string
  thumbnailPath?: string
  fileUrl?: string
  thumbnailUrl?: string
  assetType: AssetType
  manualId?: number
  createdAt?: string
}

export interface ImportJobResponse {
  id: number
  sourceFilename: string
  storedPath: string
  fileExtension: string
  status: 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED'
  languageCode: LanguageCode
  manualVersionId?: number
  detectedSections?: number
  detectedTables?: number
  detectedImages?: number
  logMessage?: string
  createdAt?: string
  completedAt?: string
}

export interface ExportJobResponse {
  id: number
  manualVersionId: number
  status: 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED'
  pdfPath?: string
  logMessage?: string
  createdAt?: string
  completedAt?: string
}

export interface TemplateResponse {
  id: number
  name: string
  description?: string
  companyName?: string
  contactEmail?: string
  contactPhone?: string
  website?: string
  logoPath?: string
  logoAssetId?: number | null
  logoUrl?: string
  headerConfigJson?: string
  footerConfigJson?: string
  active: boolean
  createdAt?: string
  updatedAt?: string
}

export type NoticeType = 'WARNING' | 'ALERT' | 'NOTE' | 'SUGGESTION'

export interface NoticeTemplateResponse {
  id: number
  code: string
  type: NoticeType
  titleEs: string
  titleEn?: string
  visibleTitleEs?: string
  visibleTitleEn?: string
  productCategory?: string
  productCodes?: string
  contentEs: string
  contentEn?: string
  active: boolean
  createdAt?: string
  updatedAt?: string
}

export interface NoticeTemplateRequest {
  code?: string
  type: NoticeType
  titleEs: string
  titleEn?: string
  visibleTitleEs?: string
  visibleTitleEn?: string
  productCategory?: string
  productCodes?: string
  contentEs: string
  contentEn?: string
  active: boolean
}

export interface NoticeUsageResponse {
  manualId: number
  manualCode: string
  manualTitle: string
  productCode: string
  sectionId?: number
  sectionNumber?: string
  sectionTitle?: string
  blockId?: number
}

export interface ReusableBlockResponse {
  id: number
  code: string
  title: string
  productCategory?: string
  productCodes?: string
  contentJson: string
  active: boolean
  createdAt?: string
  updatedAt?: string
}

export interface ReusableBlockRequest {
  code: string
  title: string
  productCategory?: string
  productCodes?: string
  contentJson: string
  active: boolean
}

export interface ReusableBlockUsageResponse {
  manualId: number
  manualCode: string
  manualTitle: string
  productCode: string
  sectionId?: number
  sectionNumber?: string
  sectionTitle?: string
  blockId?: number
}
