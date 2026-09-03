import { Button, Empty, Popconfirm, Table, Tag, Typography } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import type { Student } from '@/features/classroom/model/types'
import { formatClassDate } from '@/features/classroom/model/groupClasses'

type StudentTableProps = {
  students: Student[]
  removingStudentId?: string
  onRemove: (student: Student) => Promise<void>
}

export function StudentTable({ students, removingStudentId, onRemove }: StudentTableProps) {
  const columns: ColumnsType<Student> = [
    {
      title: '学号',
      dataIndex: 'studentId',
      key: 'studentId',
      render: (value: string) => <Typography.Text code>{value}</Typography.Text>,
    },
    {
      title: '姓名',
      dataIndex: 'studentName',
      key: 'studentName',
    },
    {
      title: '来源',
      dataIndex: 'source',
      key: 'source',
      width: 120,
      render: (value?: string) => (
        <Tag color={value === 'manual' ? 'blue' : 'green'}>
          {value === 'manual' ? '手动导入' : '自动加入'}
        </Tag>
      ),
    },
    {
      title: '加入时间',
      dataIndex: 'joinedAt',
      key: 'joinedAt',
      width: 170,
      render: (value: string) => formatClassDate(value),
    },
    {
      title: '操作',
      key: 'actions',
      width: 100,
      render: (_, student) => (
        <Popconfirm
          title={`移除学生「${student.studentName}」？`}
          description="移除后该学生将不再属于当前班级。"
          okText="移除"
          cancelText="取消"
          okButtonProps={{ danger: true }}
          onConfirm={() => void onRemove(student)}
        >
          <Button
            danger
            type="link"
            loading={removingStudentId === student.studentId}
            aria-label={`移除学生 ${student.studentName}`}
          >
            移除
          </Button>
        </Popconfirm>
      ),
    },
  ]

  return (
    <Table<Student>
      rowKey="studentId"
      columns={columns}
      dataSource={students}
      pagination={{ pageSize: 10, showSizeChanger: true, showTotal: (total) => `共 ${total} 人` }}
      locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无学生" /> }}
      scroll={{ x: 720 }}
    />
  )
}
