import { useEffect, useMemo, useState } from 'react'
import {
  App,
  Alert,
  Button,
  Card,
  Col,
  Descriptions,
  Input,
  Popconfirm,
  Result,
  Row,
  Skeleton,
  Space,
  Statistic,
  Tag,
  Tooltip,
  Typography,
} from 'antd'
import { useQuery } from '@tanstack/react-query'
import { Link, useNavigate, useParams, useSearchParams } from 'react-router'
import {
  classDetailQueryOptions,
  coursesQueryOptions,
} from '@/features/classroom/api/classroomQueries'
import {
  ClassEditorModal,
  type ClassFormValues,
} from '@/features/classroom/components/ClassEditorModal'
import { InviteModal } from '@/features/classroom/components/InviteModal'
import { StudentImportModal } from '@/features/classroom/components/StudentImportModal'
import { StudentTable } from '@/features/classroom/components/StudentTable'
import { useClassDetailMutations } from '@/features/classroom/hooks/useClassroomMutations'
import { formatClassDate } from '@/features/classroom/model/groupClasses'
import type {
  ClassPayload,
  ImportResult,
  ImportStudent,
  Student,
} from '@/features/classroom/model/types'
import { getApiErrorMessage } from '@/shared/api/errors'
import styles from './ClassDetailPage.module.css'

const EMPTY_STUDENTS: Student[] = []

export function ClassDetailPage() {
  const params = useParams<{ id: string }>()
  const navigate = useNavigate()
  const [searchParams, setSearchParams] = useSearchParams()
  const { message, modal } = App.useApp()
  const parsedClassId = Number(params.id)
  const validClassId = Number.isSafeInteger(parsedClassId) && parsedClassId > 0
  const classId = validClassId ? parsedClassId : 0
  const detailQuery = useQuery({ ...classDetailQueryOptions(classId), enabled: validClassId })
  const coursesQuery = useQuery(coursesQueryOptions())
  const mutations = useClassDetailMutations(classId)
  const [studentSearch, setStudentSearch] = useState(() => searchParams.get('student') || '')
  const [editorOpen, setEditorOpen] = useState(false)
  const [inviteOpen, setInviteOpen] = useState(false)
  const [importOpen, setImportOpen] = useState(false)

  const data = detailQuery.data
  const students = data?.students || EMPTY_STUDENTS
  const filteredStudents = useMemo(() => {
    const keyword = studentSearch.trim().toLocaleLowerCase('zh-CN')
    if (!keyword) return students
    return students.filter(
      (student) =>
        student.studentId.toLocaleLowerCase('zh-CN').includes(keyword) ||
        student.studentName.toLocaleLowerCase('zh-CN').includes(keyword),
    )
  }, [studentSearch, students])

  const editInitialValues = useMemo<Partial<ClassFormValues> | undefined>(() => {
    if (!data) return undefined
    return {
      name: data.classInfo.name,
      description: data.classInfo.description,
      courseId: data.classInfo.courseId || undefined,
      courseGroup: data.classInfo.courseGroup,
      qqGroupId: data.classInfo.qqGroupId,
    }
  }, [data])

  useEffect(() => {
    const timeout = window.setTimeout(() => {
      setSearchParams(
        (current) => {
          const next = new URLSearchParams(current)
          if (studentSearch) next.set('student', studentSearch)
          else next.delete('student')
          return next
        },
        { replace: true },
      )
    }, 200)
    return () => window.clearTimeout(timeout)
  }, [setSearchParams, studentSearch])

  async function handleUpdate(payload: ClassPayload) {
    try {
      await mutations.updateClass.mutateAsync(payload)
      setEditorOpen(false)
      message.success('班级信息已保存')
    } catch (error: unknown) {
      message.error(getApiErrorMessage(error, '保存班级失败'))
    }
  }

  async function handleToggleArchive() {
    try {
      await mutations.toggleArchive.mutateAsync()
      message.success(data?.classInfo.status === 'ACTIVE' ? '班级已归档' : '班级已恢复')
    } catch (error: unknown) {
      message.error(getApiErrorMessage(error, '更新班级状态失败'))
    }
  }

  function confirmDelete() {
    if (students.length > 0) {
      message.warning('班级下还有学生，请先移除所有学生后再删除')
      return
    }
    modal.confirm({
      title: `删除班级「${data?.classInfo.name || ''}」？`,
      content: '删除后无法恢复。',
      okText: '删除班级',
      cancelText: '取消',
      okButtonProps: { danger: true },
      onOk: async () => {
        try {
          await mutations.deleteClass.mutateAsync()
          message.success('班级已删除')
          void navigate('/teacher/classes', { replace: true })
        } catch (error: unknown) {
          message.error(getApiErrorMessage(error, '删除班级失败'))
          throw error
        }
      },
    })
  }

  async function handleRemoveStudent(student: Student) {
    try {
      await mutations.removeStudent.mutateAsync(student.studentId)
      message.success(`已移除 ${student.studentName}`)
    } catch (error: unknown) {
      message.error(getApiErrorMessage(error, '移除学生失败'))
    }
  }

  async function handleImport(studentList: ImportStudent[]): Promise<ImportResult> {
    try {
      return await mutations.importStudents.mutateAsync(studentList)
    } catch (error: unknown) {
      message.error(getApiErrorMessage(error, '导入学生失败'))
      throw error
    }
  }

  if (!validClassId) {
    return (
      <Result
        status="404"
        title="班级地址无效"
        extra={<Link to="/teacher/classes">返回班级列表</Link>}
      />
    )
  }

  if (detailQuery.isPending) {
    return (
      <Card variant="borderless">
        <Skeleton active paragraph={{ rows: 8 }} />
      </Card>
    )
  }

  if (detailQuery.isError || !data) {
    return (
      <Alert
        type="error"
        showIcon
        message="班级详情加载失败"
        description={getApiErrorMessage(detailQuery.error, '无法加载班级详情')}
        action={<Button onClick={() => void detailQuery.refetch()}>重试</Button>}
      />
    )
  }

  const classInfo = data.classInfo
  const archived = classInfo.status === 'ARCHIVED'

  return (
    <main className={styles.page}>
      <div className={styles.breadcrumb}>
        <Link to="/teacher/classes">班级列表</Link>
        <span>/</span>
        <Typography.Text>{classInfo.name}</Typography.Text>
      </div>

      <Card className={styles.infoCard} variant="borderless">
        <div className={styles.infoHeader}>
          <div>
            <Space wrap>
              <Typography.Title level={2}>{classInfo.name}</Typography.Title>
              <Tag color={archived ? 'default' : 'success'}>{archived ? '已归档' : '进行中'}</Tag>
            </Space>
            <Typography.Paragraph type="secondary">
              {classInfo.description || '暂无班级描述'}
            </Typography.Paragraph>
          </div>
          <Space wrap>
            <Button
              type="primary"
              disabled={archived}
              onClick={() => void navigate(`/teacher/live/${classId}`)}
            >
              开始实时课堂
            </Button>
            <Button onClick={() => setEditorOpen(true)}>编辑信息</Button>
            <Button onClick={() => setInviteOpen(true)}>邀请学生</Button>
            <Button onClick={() => setImportOpen(true)}>导入学生</Button>
            <Popconfirm
              title={archived ? '恢复这个班级？' : '归档这个班级？'}
              okText={archived ? '恢复' : '归档'}
              cancelText="取消"
              onConfirm={() => void handleToggleArchive()}
            >
              <Button loading={mutations.toggleArchive.isPending}>
                {archived ? '恢复班级' : '归档班级'}
              </Button>
            </Popconfirm>
            <Tooltip title={students.length ? '请先移除所有学生' : undefined}>
              <Button danger disabled={students.length > 0} onClick={confirmDelete}>
                删除班级
              </Button>
            </Tooltip>
          </Space>
        </div>

        <Descriptions column={{ xs: 1, sm: 2, lg: 4 }}>
          <Descriptions.Item label="课程分组">
            {classInfo.courseGroup || '未分组'}
          </Descriptions.Item>
          <Descriptions.Item label="QQ 群号">{classInfo.qqGroupId || '未绑定'}</Descriptions.Item>
          <Descriptions.Item label="邀请码">
            <Button type="link" onClick={() => setInviteOpen(true)}>
              {classInfo.inviteCode}
            </Button>
          </Descriptions.Item>
          <Descriptions.Item label="创建时间">
            {formatClassDate(classInfo.createdAt)}
          </Descriptions.Item>
        </Descriptions>
      </Card>

      <Row gutter={[16, 16]} className={styles.stats}>
        <Col xs={24} sm={8}>
          <Card variant="borderless">
            <Statistic title="学生人数" value={students.length} suffix="人" />
          </Card>
        </Col>
        <Col xs={24} sm={8}>
          <Card variant="borderless">
            <Statistic
              title="手动导入"
              value={students.filter((student) => student.source === 'manual').length}
              suffix="人"
            />
          </Card>
        </Col>
        <Col xs={24} sm={8}>
          <Card variant="borderless">
            <Statistic
              title="自动加入"
              value={students.filter((student) => student.source !== 'manual').length}
              suffix="人"
            />
          </Card>
        </Col>
      </Row>

      <Card variant="borderless">
        <div className={styles.studentHeader}>
          <div>
            <Typography.Title level={3}>学生名单</Typography.Title>
            <Typography.Text type="secondary">共 {students.length} 名学生</Typography.Text>
          </div>
          <Input.Search
            allowClear
            value={studentSearch}
            placeholder="搜索学号或姓名"
            onChange={(event) => setStudentSearch(event.target.value)}
            style={{ width: 260 }}
          />
        </div>
        <StudentTable
          students={filteredStudents}
          removingStudentId={mutations.removeStudent.variables}
          onRemove={handleRemoveStudent}
        />
      </Card>

      <ClassEditorModal
        open={editorOpen}
        mode="edit"
        courses={coursesQuery.data || []}
        initialValues={editInitialValues}
        submitting={mutations.updateClass.isPending}
        onCancel={() => setEditorOpen(false)}
        onSubmit={handleUpdate}
      />

      <InviteModal
        open={inviteOpen}
        className={classInfo.name}
        inviteCode={classInfo.inviteCode}
        onClose={() => setInviteOpen(false)}
      />

      <StudentImportModal
        open={importOpen}
        importing={mutations.importStudents.isPending}
        onClose={() => setImportOpen(false)}
        onImport={handleImport}
      />
    </main>
  )
}
