import { useState, type FormEvent } from "react";
import { Button, DataTable, EmptyRow, Field, PageHeader, TextInput } from "../../components/ui";
import { useRecords } from "../../state/records";

const emptyForm = {
  name: "",
  studentNumber: "",
  department: "",
  course: "",
  graduationYear: "2026",
};

export function StudentsPage() {
  const { students, addStudent, updateStudent, deleteStudent, ready } = useRecords();
  const [query, setQuery] = useState("");
  const [open, setOpen] = useState(false);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [form, setForm] = useState(emptyForm);
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");

  const visible = students.filter((student) => {
    const haystack = `${student.name} ${student.studentNumber} ${student.department} ${student.course}`.toLowerCase();
    return haystack.includes(query.trim().toLowerCase());
  });

  function update(field: keyof typeof emptyForm, value: string) {
    setForm((current) => ({ ...current, [field]: value }));
  }

  async function submit(event: FormEvent) {
    event.preventDefault();
    const year = Number(form.graduationYear);
    if (!Number.isInteger(year) || year < 2000 || year > 2100) {
      setMessage("Enter a graduation year between 2000 and 2100.");
      return;
    }
    const input = {
      name: form.name.trim(),
      studentNumber: form.studentNumber.trim(),
      department: form.department.trim(),
      course: form.course.trim(),
      graduationYear: year,
    };
    try {
      if (editingId) {
        await updateStudent(editingId, input);
        setMessage(`${input.name} was updated. A changed name writes a new fingerprint on that student's certificates.`);
      } else {
        await addStudent(input);
        setMessage(`${input.name} is now on the student list.`);
      }
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "The student could not be saved.");
      return;
    }
    setForm(emptyForm);
    setEditingId(null);
    setOpen(false);
    setError("");
  }

  function edit(student: (typeof students)[number]) {
    setEditingId(student.id);
    setForm({
      name: student.name,
      studentNumber: student.studentNumber,
      department: student.department,
      course: student.course,
      graduationYear: String(student.graduationYear),
    });
    setOpen(true);
    setError("");
    setMessage("");
  }

  async function remove(student: (typeof students)[number]) {
    if (!window.confirm(`Delete ${student.name}? This is only possible when they have no certificates.`)) return;
    setError("");
    try {
      await deleteStudent(student.id);
      setMessage(`${student.name} was removed.`);
      if (editingId === student.id) {
        setEditingId(null);
        setOpen(false);
        setForm(emptyForm);
      }
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "The student could not be deleted.");
    }
  }

  return (
    <div>
      <PageHeader
        title="Students"
        lede="Register the people who can receive a certificate from this institution."
        action={
          <Button type="button" onClick={() => setOpen((value) => !value)}>
            {open ? "Close form" : "Add student"}
          </Button>
        }
      />
      {message ? <p className="mb-4 text-sm text-seal">{message}</p> : null}
      {error ? <p className="mb-4 text-sm text-[#8c2f2f]">{error}</p> : null}
      {open ? (
        <form onSubmit={submit} className="panel mb-6 grid gap-4 p-5 sm:grid-cols-2">
          <Field label="Full name">
            <TextInput value={form.name} onChange={(event) => update("name", event.target.value)} required />
          </Field>
          <Field label="Student number">
            <TextInput
              value={form.studentNumber}
              onChange={(event) => update("studentNumber", event.target.value)}
              required
            />
          </Field>
          <Field label="Department">
            <TextInput
              value={form.department}
              onChange={(event) => update("department", event.target.value)}
              required
            />
          </Field>
          <Field label="Course">
            <TextInput value={form.course} onChange={(event) => update("course", event.target.value)} required />
          </Field>
          <Field label="Graduation year">
            <TextInput
              inputMode="numeric"
              value={form.graduationYear}
              onChange={(event) => update("graduationYear", event.target.value)}
              required
            />
          </Field>
          <div className="flex items-end gap-3">
            <Button type="submit">{editingId ? "Save changes" : "Save student"}</Button>
            {editingId ? (
              <Button
                type="button"
                variant="secondary"
                onClick={() => {
                  setEditingId(null);
                  setForm(emptyForm);
                  setOpen(false);
                }}
              >
                Cancel
              </Button>
            ) : null}
          </div>
        </form>
      ) : null}
      <div className="mb-4 max-w-sm">
        <TextInput
          placeholder="Search name, number, or course"
          value={query}
          onChange={(event) => setQuery(event.target.value)}
          aria-label="Search students"
        />
      </div>
      <DataTable headers={["Name", "Number", "Department", "Course", "Year", ""]}>
        {visible.map((student) => (
          <tr key={student.id} className="border-b border-line last:border-0">
            <td className="px-4 py-3 font-medium">{student.name}</td>
            <td className="px-4 py-3">{student.studentNumber}</td>
            <td className="px-4 py-3">{student.department}</td>
            <td className="px-4 py-3">{student.course}</td>
            <td className="px-4 py-3">{student.graduationYear}</td>
            <td className="px-4 py-3 text-right">
              <button type="button" className="text-sm font-medium text-seal" onClick={() => edit(student)}>
                Edit
              </button>
              <button type="button" className="ml-3 text-sm font-medium text-[#8c2f2f]" onClick={() => void remove(student)}>
                Delete
              </button>
            </td>
          </tr>
        ))}
        {visible.length === 0 ? (
          <EmptyRow colSpan={6}>{ready ? "No students match that search." : "Loading students."}</EmptyRow>
        ) : null}
      </DataTable>
    </div>
  );
}
