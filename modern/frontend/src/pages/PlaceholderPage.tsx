import { InlineNotification } from '@carbon/react';

// Navigation target for screens that are built in later Stories (F4-F7).
export function PlaceholderPage({ title }: { title: string }) {
  return (
    <>
      <h1 className="page-title">{title}</h1>
      <InlineNotification
        kind="info"
        title="Coming soon"
        subtitle="This screen is built in a later Story."
        hideCloseButton
        lowContrast
      />
    </>
  );
}
