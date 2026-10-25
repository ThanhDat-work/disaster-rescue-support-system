param(
    [switch]$InventoryOnly
)

$ErrorActionPreference = "Stop"
$repository = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
if ((Resolve-Path ".").Path -ne $repository) {
    throw "Chạy từ thư mục gốc repository."
}
if ((git branch --show-current) -ne "feat/ltw-backend-hardening") {
    throw "Sai branch."
}
if (-not (Test-Path -LiteralPath "pom.xml" -PathType Leaf)) {
    throw "Không tìm thấy pom.xml."
}

$commit = git rev-parse HEAD
if ($LASTEXITCODE -ne 0) { throw "Không đọc được commit." }
$output = Join-Path (Split-Path -Parent $repository) (
    "LTW-CP06-osv-{0}" -f (Get-Date -Format "yyyyMMdd-HHmmss")
)
New-Item -ItemType Directory -Path $output -ErrorAction Stop | Out-Null

$templates = @(
    Get-ChildItem -Path "src/main/resources/templates" `
        -Filter "*.html" -File -Recurse
)
$cdnPattern = 'https://cdn\.jsdelivr\.net/npm/([A-Za-z0-9._-]+)@([0-9A-Za-z.+-]+)'
$cdnRows = @(
    foreach ($template in $templates) {
        $html = Get-Content -LiteralPath $template.FullName -Raw
        foreach ($tag in [regex]::Matches(
                $html, '(?is)<(?:script|link)\b[^>]*>')) {
            foreach ($hit in [regex]::Matches($tag.Value, $cdnPattern)) {
                [pscustomobject]@{
                    source = $template.FullName.Substring($repository.Length + 1)
                    line = ([regex]::Matches(
                            $html.Substring(0, $tag.Index + $hit.Index),
                            "`n")).Count + 1
                    component = $hit.Groups[1].Value
                    version = $hit.Groups[2].Value
                    sri_present = ($tag.Value -match '(?i)\bintegrity\s*=')
                    scan_status = "MANUAL_REVIEW_REQUIRED"
                }
            }
        }
    }
)
$cdnFile = Join-Path $output "cdn-inventory.csv"
if ($cdnRows.Count -gt 0) {
    $cdnRows | Sort-Object source, line, component -Unique |
        Export-Csv -LiteralPath $cdnFile -NoTypeInformation -Encoding utf8
} else {
    "source,line,component,version,sri_present,scan_status" |
        Set-Content -LiteralPath $cdnFile -Encoding utf8
}

$assets = @(git ls-files -- "src/main/resources/static/js" "src/main/resources/static/css")
if ($LASTEXITCODE -ne 0) { throw "Không lấy được asset inventory." }
$assetRows = @(
    foreach ($asset in $assets) {
        if (-not (Test-Path -LiteralPath $asset -PathType Leaf)) { continue }
        [pscustomobject]@{
            source = $asset
            sha256 = (Get-FileHash -LiteralPath $asset -Algorithm SHA256).Hash
            classification = "LOCAL_ASSET_NOT_SCA_COVERED"
        }
    }
)
$assetFile = Join-Path $output "local-assets.csv"
$assetRows | Export-Csv -LiteralPath $assetFile -NoTypeInformation -Encoding utf8

$image = "ghcr.io/google/osv-scanner@sha256:4f3b7e2c64b9d38a5febc5a7e4a3dfef1d1362f726dea7a2ef4762b46469fcd0"
@(
    "commit=$commit"
    "scanner=$image"
    "scan_source=pom.xml copied outside repository"
    "maven_scan_scope=runtime and compile; test dependencies not covered"
    "cdn_components=$($cdnRows.Count)"
    "local_asset_files=$($assetRows.Count)"
    "frontend_vulnerability_scan=NOT_COVERED_BY_OSV_POM"
) | Set-Content -LiteralPath (Join-Path $output "scan-metadata.txt") -Encoding utf8

Write-Host "Scan evidence: $output"
Write-Host "CDN inventory: $($cdnRows.Count) references (manual review required)"
Write-Host "Local JS/CSS: $($assetRows.Count) files (inventory, not vulnerability scan)"
if ($InventoryOnly) {
    Write-Host "LTW_CP06_DEPENDENCY_INVENTORY=READY"
    return
}

& docker version *> $null
if ($LASTEXITCODE -ne 0) {
    throw "Docker engine chưa chạy; OSV scan BLOCKED."
}
& docker image inspect $image *> $null
if ($LASTEXITCODE -ne 0) {
    & docker pull $image
    if ($LASTEXITCODE -ne 0) {
        throw "Không tải được OSV image; scan BLOCKED."
    }
}

Copy-Item -LiteralPath "pom.xml" -Destination (Join-Path $output "pom.xml")
$jsonFile = Join-Path $output "osv-report.json"
$logFile = Join-Path $output "osv-stderr.log"
$previousNativePreference = $PSNativeCommandUseErrorActionPreference
$PSNativeCommandUseErrorActionPreference = $false
try {
    & docker run --rm -v "${output}:/work:ro" $image `
        scan source --format=json /work `
        1> $jsonFile 2> $logFile
    $scanExitCode = $LASTEXITCODE
} finally {
    $PSNativeCommandUseErrorActionPreference = $previousNativePreference
}

if ($scanExitCode -notin @(0, 1) -or
    -not (Test-Path -LiteralPath $jsonFile -PathType Leaf)) {
    Write-Host "OSV exit code: $scanExitCode"
    throw "OSV scan BLOCKED; đọc $logFile."
}
try {
    $report = Get-Content -LiteralPath $jsonFile -Raw -Encoding utf8 |
        ConvertFrom-Json
} catch {
    throw "OSV JSON không hợp lệ; scan BLOCKED. Đọc $logFile."
}
if ($null -eq $report.results) {
    throw "OSV JSON không có results; scan BLOCKED. Đọc $logFile."
}

$findings = @(
    foreach ($result in $report.results) {
        foreach ($entry in $result.packages) {
            foreach ($vulnerability in $entry.vulnerabilities) {
                [pscustomobject]@{
                    component = $entry.package.name
                    version = $entry.package.version
                    advisory = $vulnerability.id
                    aliases = ($vulnerability.aliases -join ", ")
                    severity = "REVIEW_REQUIRED"
                    fixed_version = "RESEARCH_REQUIRED"
                    disposition = "REVIEW_REQUIRED"
                }
            }
        }
    }
)
$findingsFile = Join-Path $output "java-findings.csv"
if ($findings.Count -gt 0) {
    $findings | Sort-Object component, version, advisory -Unique |
        Export-Csv -LiteralPath $findingsFile -NoTypeInformation -Encoding utf8
} else {
    "component,version,advisory,aliases,severity,fixed_version,disposition" |
        Set-Content -LiteralPath $findingsFile -Encoding utf8
}
if ($scanExitCode -eq 1 -and $findings.Count -eq 0) {
    throw "OSV exit 1 nhưng JSON không có finding; scan BLOCKED. Đọc $logFile."
}
if ($scanExitCode -eq 0 -and $findings.Count -gt 0) {
    throw "OSV exit 0 nhưng JSON có finding; scan BLOCKED. Đọc $logFile."
}

Write-Host "OSV exit code: $scanExitCode"
Write-Host "Findings: $($findings.Count)"
Write-Host "LTW_CP06_OSV_SCAN=EXECUTED"
if ($findings.Count -gt 0) {
    Write-Host "LTW_CP06_DEPENDENCY_DECISION=PENDING"
} else {
    Write-Host "LTW_CP06_JAVA_FINDINGS=NONE_IN_POM_SCOPE"
}
