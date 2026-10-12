import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { api } from "../../api/client";
import { PageHeader } from "../../components/ui";

type Notice = { id: string; title: string; message: string; href: string; readAt: string | null; createdAt: string };
type Activity = { id: string; eventType: string; detail: string; createdAt: string };

export function ActivityPage() {
  const [notices, setNotices] = useState<Notice[]>([]);
  const [activity, setActivity] = useState<Activity[]>([]);

  useEffect(() => {
    void Promise.all([api<Notice[]>("/api/notifications"), api<Activity[]>("/api/wallet/activity")]).then(
      ([nextNotices, nextActivity]) => {
        setNotices(nextNotices);
        setActivity(nextActivity);
      },
    );
  }, []);

  async function mark(id: string) {
    await api(`/api/notifications/${id}/read`, { method: "PATCH" });
    setNotices((current) => current.map((notice) => (notice.id === id ? { ...notice, readAt: new Date().toISOString() } : notice)));
  }

  return (
    <div className="grid gap-8 lg:grid-cols-2">
      <section>
        <PageHeader title="Notifications" lede="Status changes for this account. Other people’s documents are not included." />
        {notices.length === 0 ? <p className="text-sm text-muted">No notifications yet.</p> : (
          <ul className="space-y-3">
            {notices.map((notice) => (
              <li key={notice.id} className="panel px-4 py-3 text-sm">
                <Link to={notice.href || "/wallet"} className="font-medium">{notice.title}</Link>
                <p className="mt-1 text-muted">{notice.message}</p>
                {!notice.readAt ? <button className="mt-2 text-seal" onClick={() => void mark(notice.id)}>Mark read</button> : null}
              </li>
            ))}
          </ul>
        )}
      </section>
      <section>
        <h2 className="mb-4 font-serif text-3xl">Activity</h2>
        {activity.length === 0 ? <p className="text-sm text-muted">No activity yet.</p> : (
          <ul className="space-y-3">
            {activity.map((event) => (
              <li key={event.id} className="panel px-4 py-3 text-sm">
                <p className="font-medium">{event.eventType.replaceAll("_", " ")}</p>
                <p className="mt-1 text-muted">{event.detail}</p>
              </li>
            ))}
          </ul>
        )}
      </section>
    </div>
  );
}
