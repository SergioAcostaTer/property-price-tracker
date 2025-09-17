$headers = @{ Authorization = "Bearer dev-token" }
$body = @{
  portal = "idealista"
  resources = @(
    @{ task_type="search_page"; segment="rent"; url="https://www.idealista.com/alquiler-viviendas/las-palmas/"; priority=2; next_run_in_sec=0 }
  )
} | ConvertTo-Json -Depth 6

Invoke-RestMethod -Method Post `
 -Uri "http://localhost:8080/v1/frontier/batch-upsert" `
 -Headers $headers -ContentType "application/json" -Body $body
