import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { cn } from "@/lib/utils"

interface ContentPanelProps {
  title: string
  description?: string
  className?: string
}

// placeholder for panels whose real content (tables, forms, charts) isn't built yet
export function ContentPanel({ title, description, className }: ContentPanelProps) {
  return (
    <Card className={cn("flex min-h-56 flex-1 flex-col gap-0 py-0", className)}>
      <CardHeader className="border-b border-border/60 py-4">
        <CardTitle className="text-base">{title}</CardTitle>
        {description ? <CardDescription>{description}</CardDescription> : null}
      </CardHeader>
      <CardContent className="flex flex-1 items-center justify-center py-10">
        <span className="text-sm text-muted-foreground">Content coming soon</span>
      </CardContent>
    </Card>
  )
}
