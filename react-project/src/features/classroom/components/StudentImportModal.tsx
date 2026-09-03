import { useMemo, useState } from 'react'
import { App, Button, Modal, Space, Table, Tag, Typography, Upload } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import type { UploadProps } from 'antd'
import type { ImportResult, ImportStudent } from '@/features/classroom/model/types'

type ParsedStudent = ImportStudent & {
  rowNumber: number
  valid: boolean
  error?: string
}

type StudentImportModalProps = {
  open: boolean
  importing: boolean
  onClose: () => void
  onImport: (students: ImportStudent[]) => Promise<ImportResult>
}

const acceptedExtensions = ['xlsx', 'xls', 'csv']
const idHeaders = ['学号', 'studentid', 'student_id', 'student id', 'id', '编号']
const nameHeaders = ['姓名', 'name', 'studentname', 'student_name', 'student name', '名字']

function cellText(value: unknown) {
  if (
    typeof value === 'string' ||
    typeof value === 'number' ||
    typeof value === 'boolean' ||
    typeof value === 'bigint'
  ) {
    return String(value).trim()
  }
  return ''
}

function normalizeHeader(value: unknown) {
  return cellText(value).toLowerCase()
}

export function StudentImportModal({
  open,
  importing,
  onClose,
  onImport,
}: StudentImportModalProps) {
  const { message, modal } = App.useApp()
  const [fileName, setFileName] = useState('')
  const [students, setStudents] = useState<ParsedStudent[]>([])
  const [parsing, setParsing] = useState(false)

  const validStudents = useMemo(() => students.filter((student) => student.valid), [students])
  const invalidCount = students.length - validStudents.length

  function reset() {
    setFileName('')
    setStudents([])
    setParsing(false)
  }

  async function parseFile(file: File) {
    const extension = file.name.split('.').pop()?.toLowerCase() || ''
    if (!acceptedExtensions.includes(extension)) {
      message.warning('仅支持 .xlsx / .xls / .csv 格式文件')
      return
    }

    setParsing(true)
    try {
      const XLSX = await import('xlsx')
      const workbook = XLSX.read(await file.arrayBuffer(), { type: 'array' })
      const firstSheetName = workbook.SheetNames[0]
      const sheet = firstSheetName ? workbook.Sheets[firstSheetName] : undefined
      if (!sheet) throw new Error('工作簿中没有可读取的工作表')

      const rows = XLSX.utils.sheet_to_json<unknown[]>(sheet, { header: 1 })
      const headerRow = rows[0]
      if (!headerRow || rows.length < 2) {
        throw new Error('文件为空，请确保第一行为表头（学号、姓名）')
      }

      const headers = headerRow.map(normalizeHeader)
      const idColumn = headers.findIndex((header) => idHeaders.includes(header))
      const nameColumn = headers.findIndex((header) => nameHeaders.includes(header))
      if (idColumn < 0) throw new Error('未找到「学号」列')
      if (nameColumn < 0) throw new Error('未找到「姓名」列')

      const seen = new Set<string>()
      let accepted = 0
      const parsed: ParsedStudent[] = []
      for (let index = 1; index < rows.length; index += 1) {
        const row = rows[index]
        if (!row || row.every((cell) => !cellText(cell))) continue

        const studentId = cellText(row[idColumn])
        const studentName = cellText(row[nameColumn])
        const errors: string[] = []
        if (!studentId) errors.push('学号为空')
        if (!studentName) errors.push('姓名为空')
        if (studentId && seen.has(studentId)) errors.push('学号重复')
        if (studentId) seen.add(studentId)
        if (errors.length === 0 && accepted >= 200) errors.push('超过单次200人限制')
        if (errors.length === 0) accepted += 1

        parsed.push({
          rowNumber: index + 1,
          studentId,
          studentName,
          valid: errors.length === 0,
          error: errors.join('；') || undefined,
        })
      }

      if (parsed.length === 0) throw new Error('未解析到任何学生数据')
      setFileName(file.name)
      setStudents(parsed)
    } catch (error: unknown) {
      message.error(error instanceof Error ? error.message : '文件解析失败')
      reset()
    } finally {
      setParsing(false)
    }
  }

  const uploadProps: UploadProps = {
    accept: '.xlsx,.xls,.csv',
    multiple: false,
    showUploadList: false,
    beforeUpload: (file) => {
      void parseFile(file)
      return Upload.LIST_IGNORE
    },
  }

  async function downloadTemplate() {
    try {
      const XLSX = await import('xlsx')
      const workbook = XLSX.utils.book_new()
      const sheet = XLSX.utils.aoa_to_sheet([
        ['学号', '姓名'],
        ['2021001001', '张三'],
        ['2021001002', '李四'],
      ])
      XLSX.utils.book_append_sheet(workbook, sheet, '学生名单')
      XLSX.writeFile(workbook, '学生导入模板.xlsx')
    } catch {
      message.error('导入模板生成失败')
    }
  }

  function confirmImport() {
    modal.confirm({
      title: `确认导入 ${validStudents.length} 名学生？`,
      content: invalidCount
        ? `${invalidCount} 行异常数据将被跳过。`
        : '导入后可以在班级详情中继续管理。',
      okText: '确认导入',
      cancelText: '再检查一下',
      onOk: async () => {
        const result = await onImport(
          validStudents.map(({ studentId, studentName }) => ({ studentId, studentName })),
        )
        message.success(`成功导入 ${result.imported} 名学生，跳过 ${result.skipped} 条`)
        reset()
        onClose()
      },
    })
  }

  const columns: ColumnsType<ParsedStudent> = [
    { title: '#', dataIndex: 'rowNumber', key: 'rowNumber', width: 64 },
    { title: '学号', dataIndex: 'studentId', key: 'studentId' },
    { title: '姓名', dataIndex: 'studentName', key: 'studentName' },
    {
      title: '状态',
      key: 'status',
      width: 90,
      render: (_, student) => (
        <Tag color={student.valid ? 'success' : 'error'}>{student.valid ? '有效' : '异常'}</Tag>
      ),
    },
    { title: '备注', dataIndex: 'error', key: 'error', render: (value?: string) => value || '—' },
  ]

  return (
    <Modal
      open={open}
      width={820}
      title="批量导入学生"
      destroyOnHidden
      onCancel={onClose}
      afterClose={reset}
      footer={
        students.length
          ? [
              <Button key="reset" onClick={reset}>
                重新选择
              </Button>,
              <Button
                key="import"
                type="primary"
                disabled={validStudents.length === 0}
                loading={importing}
                onClick={confirmImport}
              >
                导入 {validStudents.length} 名学生
              </Button>,
            ]
          : null
      }
    >
      {students.length === 0 ? (
        <Space direction="vertical" size={16} style={{ width: '100%' }}>
          <Upload.Dragger {...uploadProps} disabled={parsing}>
            <Typography.Title level={4}>
              {parsing ? '正在解析文件……' : '拖拽名单到这里'}
            </Typography.Title>
            <Typography.Paragraph type="secondary">
              支持 .xlsx / .xls / .csv，第一行需要包含「学号」和「姓名」，单次最多 200 人。
            </Typography.Paragraph>
          </Upload.Dragger>
          <Button onClick={() => void downloadTemplate()}>下载导入模板</Button>
        </Space>
      ) : (
        <Space direction="vertical" size={12} style={{ width: '100%' }}>
          <Space wrap>
            <Typography.Text strong>{fileName}</Typography.Text>
            <Tag color="success">有效 {validStudents.length}</Tag>
            {invalidCount > 0 && <Tag color="error">异常 {invalidCount}</Tag>}
          </Space>
          <Table<ParsedStudent>
            rowKey={(student) => `${student.rowNumber}-${student.studentId}`}
            columns={columns}
            dataSource={students.slice(0, 50)}
            size="small"
            pagination={false}
            scroll={{ x: 620, y: 360 }}
          />
          {students.length > 50 && (
            <Typography.Text type="secondary">
              仅预览前 50 行，共 {students.length} 行。
            </Typography.Text>
          )}
        </Space>
      )}
    </Modal>
  )
}
