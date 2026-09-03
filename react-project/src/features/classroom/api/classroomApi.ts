import apiClient from '@/shared/api/client'
import type { ApiResponse } from '@/shared/api/types'
import { unwrapApiResponse } from '@/shared/api/unwrap'
import type {
  ClassDetail,
  ClassDetailData,
  ClassGroupResponse,
  ClassPayload,
  Course,
  CoursePayload,
  ImportResult,
  ImportStudent,
  PresetTemplate,
  Student,
} from '@/features/classroom/model/types'

type RawStudent = {
  studentId: string
  studentName: string
  createdAt?: string
  joinedAt?: string
  source?: string
}

type RawClassDetailData = {
  class: ClassDetail
  students?: RawStudent[]
}

export async function listClassGroups(): Promise<ClassGroupResponse[]> {
  const response = await apiClient.get<ApiResponse<ClassGroupResponse[]>>('/teacher/classes')
  return unwrapApiResponse(response.data, '加载班级失败') || []
}

export async function createClass(payload: ClassPayload): Promise<ClassDetail> {
  const response = await apiClient.post<ApiResponse<ClassDetail>>('/teacher/classes', payload)
  return unwrapApiResponse(response.data, '创建班级失败')
}

export async function getClassDetail(classId: number): Promise<ClassDetailData> {
  const response = await apiClient.get<ApiResponse<RawClassDetailData>>(
    `/teacher/classes/${classId}`,
  )
  const data = unwrapApiResponse(response.data, '加载班级详情失败')
  const students: Student[] = (data.students || []).map((student) => ({
    studentId: student.studentId,
    studentName: student.studentName,
    joinedAt: student.createdAt || student.joinedAt || '',
    source: student.source,
  }))
  return {
    classInfo: {
      ...data.class,
      courseGroup: data.class.courseGroup || '',
      courseId: data.class.courseId || null,
      qqGroupId: data.class.qqGroupId || '',
      description: data.class.description || '',
    },
    students,
  }
}

export async function updateClass(classId: number, payload: ClassPayload): Promise<void> {
  const response = await apiClient.put<ApiResponse<null>>(`/teacher/classes/${classId}`, payload)
  unwrapApiResponse(response.data, '保存班级失败')
}

export async function toggleClassArchive(classId: number): Promise<void> {
  const response = await apiClient.post<ApiResponse<null>>(`/teacher/classes/${classId}/archive`)
  unwrapApiResponse(response.data, '更新班级状态失败')
}

export async function deleteClass(classId: number): Promise<void> {
  const response = await apiClient.delete<ApiResponse<null>>(`/teacher/classes/${classId}`)
  unwrapApiResponse(response.data, '删除班级失败')
}

export async function removeStudent(classId: number, studentId: string): Promise<void> {
  const encodedStudentId = encodeURIComponent(studentId)
  const response = await apiClient.delete<ApiResponse<null>>(
    `/teacher/classes/${classId}/students/${encodedStudentId}`,
  )
  unwrapApiResponse(response.data, '移除学生失败')
}

export async function importStudents(
  classId: number,
  students: ImportStudent[],
): Promise<ImportResult> {
  const response = await apiClient.post<ApiResponse<ImportResult>>(
    `/teacher/classes/${classId}/students/import`,
    { students },
  )
  return unwrapApiResponse(response.data, '导入学生失败')
}

export async function listCourses(): Promise<Course[]> {
  const response = await apiClient.get<ApiResponse<Course[]>>('/courses')
  return unwrapApiResponse(response.data, '加载课程失败') || []
}

export async function getCoursePresets(): Promise<Record<string, PresetTemplate>> {
  const response =
    await apiClient.get<ApiResponse<Record<string, PresetTemplate>>>('/courses/presets')
  return unwrapApiResponse(response.data, '加载课程预设失败') || {}
}

export async function createCourse(payload: CoursePayload): Promise<Course> {
  const response = await apiClient.post<ApiResponse<Course>>('/courses', payload)
  return unwrapApiResponse(response.data, '创建课程失败')
}

export async function updateCourse(courseId: number, payload: CoursePayload): Promise<void> {
  const response = await apiClient.put<ApiResponse<null>>(`/courses/${courseId}`, payload)
  unwrapApiResponse(response.data, '更新课程失败')
}

export async function deleteCourse(courseId: number): Promise<void> {
  const response = await apiClient.delete<ApiResponse<null>>(`/courses/${courseId}`)
  unwrapApiResponse(response.data, '删除课程失败')
}
