import type { ClassGroupResponse, ClassItem, Course, CourseGroup } from './types'

const UNGROUPED_KEY = '__ungrouped__'

export function flattenClassGroups(groups: ClassGroupResponse[]): ClassItem[] {
  return groups.flatMap((group) =>
    (group.classes || []).map((classItem) => ({
      ...classItem,
      courseGroup: classItem.courseGroup || group.courseGroup || '',
      courseId: classItem.courseId || group.courseId || null,
      qqGroupId: classItem.qqGroupId || '',
      description: classItem.description || '',
      studentCount: classItem.studentCount || 0,
    })),
  )
}

export function buildCourseGroups(
  classGroups: ClassGroupResponse[],
  courses: Course[],
): CourseGroup[] {
  const classes = flattenClassGroups(classGroups)
  const courseNameById = new Map(courses.map((course) => [course.id, course.name]))
  const grouped = new Map<string, { courseId: number | null; classes: ClassItem[] }>()

  for (const classItem of classes) {
    const groupName =
      classItem.courseGroup ||
      (classItem.courseId ? courseNameById.get(classItem.courseId) : undefined) ||
      UNGROUPED_KEY
    const current = grouped.get(groupName) || { courseId: classItem.courseId, classes: [] }
    current.classes.push(classItem)
    grouped.set(groupName, current)
  }

  for (const course of courses) {
    const current = grouped.get(course.name)
    if (current) {
      current.courseId ??= course.id
    } else {
      grouped.set(course.name, { courseId: course.id, classes: [] })
    }
  }

  return [...grouped.entries()]
    .sort(([left], [right]) => {
      if (left === UNGROUPED_KEY) return 1
      if (right === UNGROUPED_KEY) return -1
      return left.localeCompare(right, 'zh-CN')
    })
    .map(([key, value]) => {
      const sortedClasses = [...value.classes].sort((left, right) => {
        if (left.status !== right.status) return left.status === 'ACTIVE' ? -1 : 1
        return new Date(right.createdAt).getTime() - new Date(left.createdAt).getTime()
      })
      return {
        key,
        name: key === UNGROUPED_KEY ? '未分组班级' : key,
        courseId: value.courseId,
        classes: sortedClasses,
        totalStudents: sortedClasses.reduce((total, item) => total + item.studentCount, 0),
        activeCount: sortedClasses.filter((item) => item.status === 'ACTIVE').length,
      }
    })
}

export function filterCourseGroups(groups: CourseGroup[], search: string) {
  const keyword = search.trim().toLocaleLowerCase('zh-CN')
  if (!keyword) return groups

  return groups
    .map((group) => ({
      ...group,
      classes: group.classes.filter(
        (classItem) =>
          classItem.name.toLocaleLowerCase('zh-CN').includes(keyword) ||
          group.name.toLocaleLowerCase('zh-CN').includes(keyword),
      ),
    }))
    .filter((group) => group.classes.length > 0)
}

export function formatClassDate(value: string) {
  if (!value) return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleDateString('zh-CN', { year: 'numeric', month: 'long', day: 'numeric' })
}
