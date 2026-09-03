import { queryOptions } from '@tanstack/react-query'
import { getClassDetail, getCoursePresets, listClassGroups, listCourses } from './classroomApi'

export const classroomKeys = {
  all: ['classroom'] as const,
  classGroups: () => [...classroomKeys.all, 'class-groups'] as const,
  classDetails: () => [...classroomKeys.all, 'class-detail'] as const,
  classDetail: (classId: number) => [...classroomKeys.classDetails(), classId] as const,
  courses: () => [...classroomKeys.all, 'courses'] as const,
  coursePresets: () => [...classroomKeys.all, 'course-presets'] as const,
}

export const classGroupsQueryOptions = () =>
  queryOptions({
    queryKey: classroomKeys.classGroups(),
    queryFn: listClassGroups,
    staleTime: 60_000,
  })

export const coursesQueryOptions = () =>
  queryOptions({
    queryKey: classroomKeys.courses(),
    queryFn: listCourses,
    staleTime: 5 * 60_000,
  })

export const coursePresetsQueryOptions = () =>
  queryOptions({
    queryKey: classroomKeys.coursePresets(),
    queryFn: getCoursePresets,
    staleTime: Number.POSITIVE_INFINITY,
  })

export const classDetailQueryOptions = (classId: number) =>
  queryOptions({
    queryKey: classroomKeys.classDetail(classId),
    queryFn: () => getClassDetail(classId),
    staleTime: 30_000,
  })
