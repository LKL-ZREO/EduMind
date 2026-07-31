export interface FlatNode {
  id: number
  userId: number
  parentId: number | null
  label: string
  nodeType: 'folder' | 'file'
  docId: string | null
  sortOrder: number
  createdAt: string
  updatedAt: string
  kbId?: number
}

export interface TreeNode {
  id: number
  label: string
  type: 'folder' | 'file'
  docId?: string
  children?: TreeNode[]
  content?: string
  kbId?: number
  createdAt?: string
  updatedAt?: string
  loadState?: 'idle' | 'loading' | 'ready' | 'processing' | 'error'
}

export interface SharedKb {
  id: number
  name: string
  description: string
  ownerId: number
  inviteToken?: string
  createdAt: string
}

export type KbRole = 'owner' | 'admin' | 'member'

export interface KbMember {
  userId?: number
  user_id?: number
  username: string
  role: KbRole
}

export interface GeneratedOption {
  key: string
  text: string
}

export interface GeneratedQuestion {
  id?: number
  type: string
  quizType?: string
  question?: string
  title: string
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

export interface GeneratedPreview {
  id?: number
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

export interface GenResult {
  preview: GeneratedPreview | null
  quizzes: GeneratedQuestion[]
  pptFileName: string
  previewError?: string
  quizError?: string
}

export interface SavedPreview extends GeneratedPreview {
  id: number
}

export interface SavedQuestion extends GeneratedQuestion {
  id: number
}

export interface TeachingMaterials {
  previews: SavedPreview[]
  questions: SavedQuestion[]
}

export type DraftDetail =
  | { type: 'preview'; data: GeneratedPreview }
  | { type: 'quiz'; data: GeneratedQuestion }
