export type ClassStatus = 'ACTIVE' | 'ARCHIVED'

export type ClassItem = {
  id: number
  name: string
  courseGroup: string
  courseId: number | null
  qqGroupId: string
  description: string
  studentCount: number
  inviteCode: string
  status: ClassStatus
  createdAt: string
}

export type ClassGroupResponse = {
  courseGroup: string | null
  courseId: number | null
  classes: ClassItem[]
}

export type Course = {
  id: number
  name: string
  systemPrompt: string
  knowledgeScope: string
  teacherId: number
  createdAt: string
  updatedAt: string
}

export type PresetTemplate = {
  key: string
  name: string
  prompt: string
}

export type CourseGroup = {
  key: string
  name: string
  courseId: number | null
  classes: ClassItem[]
  totalStudents: number
  activeCount: number
}

export type ClassDetail = Omit<ClassItem, 'studentCount'> & {
  updatedAt?: string
}

export type Student = {
  studentId: string
  studentName: string
  joinedAt: string
  source?: string
}

export type ClassDetailData = {
  classInfo: ClassDetail
  students: Student[]
}

export type ClassPayload = {
  name: string
  description?: string
  courseId?: number
  courseGroup?: string
  qqGroupId?: string
}

export type CoursePayload = {
  name: string
  presetKey?: string
  systemPrompt?: string
  knowledgeScope?: string
}

export type ImportStudent = {
  studentId: string
  studentName: string
}

export type ImportResult = {
  imported: number
  skipped: number
}
