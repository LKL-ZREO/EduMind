import { useMutation, useQueryClient } from '@tanstack/react-query'
import {
  createClass,
  createCourse,
  deleteClass,
  deleteCourse,
  importStudents,
  removeStudent,
  toggleClassArchive,
  updateClass,
  updateCourse,
} from '@/features/classroom/api/classroomApi'
import { classroomKeys } from '@/features/classroom/api/classroomQueries'
import type { ClassPayload, CoursePayload, ImportStudent } from '@/features/classroom/model/types'

export function useCreateClassMutation() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: createClass,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: classroomKeys.classGroups() }),
  })
}

export function useCourseMutations() {
  const queryClient = useQueryClient()
  const invalidateCourseContext = async () => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: classroomKeys.courses() }),
      queryClient.invalidateQueries({ queryKey: classroomKeys.classGroups() }),
    ])
  }

  return {
    createCourse: useMutation({
      mutationFn: createCourse,
      onSuccess: invalidateCourseContext,
    }),
    updateCourse: useMutation({
      mutationFn: ({ courseId, payload }: { courseId: number; payload: CoursePayload }) =>
        updateCourse(courseId, payload),
      onSuccess: invalidateCourseContext,
    }),
    deleteCourse: useMutation({
      mutationFn: deleteCourse,
      onSuccess: invalidateCourseContext,
    }),
  }
}

export function useClassDetailMutations(classId: number) {
  const queryClient = useQueryClient()
  const invalidateClassContext = async () => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: classroomKeys.classDetail(classId) }),
      queryClient.invalidateQueries({ queryKey: classroomKeys.classGroups() }),
    ])
  }

  return {
    updateClass: useMutation({
      mutationFn: (payload: ClassPayload) => updateClass(classId, payload),
      onSuccess: invalidateClassContext,
    }),
    toggleArchive: useMutation({
      mutationFn: () => toggleClassArchive(classId),
      onSuccess: invalidateClassContext,
    }),
    deleteClass: useMutation({
      mutationFn: () => deleteClass(classId),
      onSuccess: async () => {
        queryClient.removeQueries({ queryKey: classroomKeys.classDetail(classId) })
        await queryClient.invalidateQueries({ queryKey: classroomKeys.classGroups() })
      },
    }),
    removeStudent: useMutation({
      mutationFn: (studentId: string) => removeStudent(classId, studentId),
      onSuccess: invalidateClassContext,
    }),
    importStudents: useMutation({
      mutationFn: (students: ImportStudent[]) => importStudents(classId, students),
      onSuccess: invalidateClassContext,
    }),
  }
}
