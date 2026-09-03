export type FlatNode = {
  id: number
  userId: number
  parentId: number | null
  label: string
  nodeType: 'folder' | 'file'
  docId: string | null
  sortOrder: number
  isShared?: boolean
  createdAt: string
  updatedAt: string
  kbId?: number
}

export type TreeNode = {
  id: number
  label: string
  type: 'folder' | 'file'
  docId?: string
  children: TreeNode[]
  kbId?: number
  isShared?: boolean
  createdAt?: string
  updatedAt?: string
}

export type SharedKb = {
  id: number
  name: string
  description: string
  ownerId: number
  inviteToken?: string
  inviteExpiresAt?: string
  inviteMaxUses?: number
  inviteUseCount?: number
  createdAt: string
  updatedAt?: string
}

export type KbRole = 'owner' | 'admin' | 'member'

export type KbMember = {
  userId: number
  username: string
  role: KbRole
}

export type GeneratedOption = {
  key: string
  text: string
}

export type GeneratedQuestion = {
  id?: number
  type: string
  quizType?: string
  question?: string
  title: string
  requirement?: string
  difficulty?: string
  knowledgePoint?: string
  options?: GeneratedOption[]
  correctKey?: string
  explanation?: string
  timeLimit?: number
  published?: boolean
  status?: string
  archived?: boolean
  createdAt?: string
}

export type GeneratedPreview = {
  id?: number
  savedId?: number
  topic?: string
  title?: string
  knowledgePoint?: string
  guideText?: string
  discussionQuestion?: string
  questions?: GeneratedQuestion[]
  published?: boolean
  status?: string
  createdAt?: string
}

export type GenResult = {
  preview: GeneratedPreview | null
  quizzes: GeneratedQuestion[]
  pptFileName: string
  previewError?: string
  quizError?: string
}

export type SavedPreview = GeneratedPreview & { id: number }
export type SavedQuestion = GeneratedQuestion & { id: number }

export type TeachingMaterials = {
  previews: SavedPreview[]
  questions: SavedQuestion[]
}

export type DraftDetail =
  { type: 'preview'; data: GeneratedPreview } | { type: 'quiz'; data: GeneratedQuestion }

export type KnowledgeSpaces = {
  owned: SharedKb[]
  joined: SharedKb[]
}

export type ClassroomOption = { id: number; name: string }

export type FolderPayload = {
  label: string
  parentId?: number
  kbId: number | null
}

export type UploadDocumentsPayload = {
  files: File[]
  parentNodeId?: number
  kbId: number | null
  onProgress?: (percent: number, fileName: string) => void
}

export type GenerateMaterialsPayload = {
  docId: string
  classId?: number | null
}

export type SavePreviewPayload = GeneratedPreview & {
  classId: number
  docId: string
}
