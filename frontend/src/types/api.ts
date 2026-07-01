export type BackendRole = 'ADMIN' | 'TECNICO' | 'REVISOR' | 'CLIENTE'
export type ManualStatus = 'DRAFT' | 'REVIEW' | 'APPROVED' | 'PUBLISHED' | 'ARCHIVED' | 'DEACTIVATED'
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
  | 'INFO_BOX'
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
  nameEs?: string
  nameEn?: string
  familyId?: number
  familyCode?: string
  family?: string
  familyInfo?: ProductFamilyResponse
  categoryIds?: number[]
  categoryCodes?: string
  category?: string
  categories?: ProductCategoryResponse[]
  description?: string
  descriptionEs?: string
  descriptionEn?: string
  productImageAssetId?: number
  productImageUrl?: string
  productImageThumbnailUrl?: string
  active: boolean
  createdAt?: string
  updatedAt?: string
}

export interface ProductDeleteImpactResponse {
  productId: number
  hasManualHistory: boolean
  relatedManuals: ManualSummaryResponse[]
  activeManuals: ManualSummaryResponse[]
}

export interface ProductFamilyResponse {
  id: number
  code: string
  nameEs: string
  nameEn?: string
}

export interface ProductCategoryResponse {
  id: number
  code: string
  nameEs: string
  nameEn?: string
}

export interface ProductRequest {
  code: string
  name: string
  nameEs?: string
  nameEn?: string
  familyId?: number
  family?: string
  familyCode?: string
  categoryIds?: number[]
  category?: string
  categoryCodes?: string
  description?: string
  descriptionEs?: string
  descriptionEn?: string
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
  parentSectionId?: number
  linkedReusableSectionId?: number
  level: number
  titleEs: string
  titleEn?: string
  completionStatus?: string
  visible: boolean
  blocks: ManualBlockResponse[]
}

export interface ManualBlockResponse {
  id: number
  sortOrder: number
  blockType: BlockType
  languageCode: LanguageCode
  contentJson: string
  plainText?: string
  assetId?: number
  reusableBlockId?: number
  reusableFragmentId?: number
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
  parentSectionId?: number
  linkedReusableSectionId?: number
  level?: number
  titleEs: string
  titleEn?: string
  completionStatus?: string
  visible?: boolean
  blocks: ManualBlockRequest[]
}

export interface ManualBlockRequest {
  id?: number
  sortOrder: number
  blockType: BlockType
  languageCode: LanguageCode
  contentJson: string
  plainText?: string
  assetId?: number
  reusableBlockId?: number
  reusableFragmentId?: number
}

export type ManualTranslationMode = 'FULL_MANUAL' | 'SELECTED_SECTIONS'

export interface ManualTranslationRequest {
  mode: ManualTranslationMode
  sectionIds?: number[]
  overwriteExisting?: boolean
}

export interface ManualTranslationResponse {
  status: 'COMPLETED' | 'PARTIAL' | 'FAILED'
  manualVersionId: number
  targetLanguage: LanguageCode
  translatedSections: number
  translatedBlocks: number
  skippedSections: number
  errors: string[]
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
  layoutConfigJson?: string
  active: boolean
  systemDefault?: boolean
  createdAt?: string
  updatedAt?: string
}

export type NoticeType = 'WARNING' | 'ALERT' | 'NOTE' | 'SUGGESTION'

export interface NoticeTemplateResponse {
  id: number
  code: string
  type: NoticeType
  title: string
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
  title: string
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
  titleEs?: string
  titleEn?: string
  description?: string
  descriptionEs?: string
  descriptionEn?: string
  reusableType: 'SINGLE_BLOCK' | 'FRAGMENT' | 'NOTE'
  productCategory?: string
  productCodes?: string
  contentJson: string
  active: boolean
  createdAt?: string
  updatedAt?: string
}

export interface ReusableFragmentResponse {
  id: number
  code: string
  title: string
  description?: string
  productCategory?: string
  productCodes?: string
  contentJson: string
  active: boolean
  createdAt?: string
  updatedAt?: string
  reusableType?: 'FRAGMENT'
}

export interface ReusableFragmentRequest {
  code?: string
  title: string
  description?: string
  productCategory?: string
  productCodes?: string
  contentJson: string
  active: boolean
}

export interface ReusableBlockRequest {
  code?: string
  title?: string
  titleEs: string
  titleEn?: string
  description?: string
  descriptionEs?: string
  descriptionEn?: string
  reusableType?: 'SINGLE_BLOCK' | 'FRAGMENT' | 'NOTE'
  productCategory?: string
  productCodes?: string
  contentJson: string
  active: boolean
}

export interface ReusableFragmentBlockRequest {
  blockType: BlockType
  languageCode: LanguageCode
  contentJson: string
  plainText?: string
  assetId?: number
  sortOrder?: number
}

export interface CreateReusableFragmentRequest {
  name: string
  description?: string
  sourceSectionId?: number
  blockIds?: number[]
  blocks?: ReusableFragmentBlockRequest[]
  isReusable: boolean
}

export interface InsertReusableFragmentRequest {
  targetSectionId: number
  insertAfterBlockId?: number
  mode: 'COPY'
}

export interface ReusableFragmentInsertResponse {
  reusableBlockId?: number
  reusableFragmentId?: number
  targetSectionId: number
  blocks: ManualBlockResponse[]
  insertedBlocks?: ManualBlockResponse[]
}

export interface OrderItemRequest {
  id: number
  sortOrder: number
}

export interface ReorderRequest {
  items: OrderItemRequest[]
}

export interface ManualSectionPatchRequest {
  titleEs?: string
  titleEn?: string
  completionStatus?: string
  visible?: boolean
}

export interface MoveBlockRequest {
  targetSectionId: number
  insertAfterBlockId?: number
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

export type ReusableFragmentUsageResponse = ReusableBlockUsageResponse
