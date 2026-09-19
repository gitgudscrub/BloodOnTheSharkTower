param(
    [Parameter(Mandatory=$true)]
    [string]$Path
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path $Path)) {
    throw "Path not found: $Path"
}

$extensions = @("*.java", "*.json", "*.properties", "*.md", "*.txt")
$files = foreach ($extension in $extensions) {
    Get-ChildItem -Path $Path -Recurse -File -Filter $extension -ErrorAction SilentlyContinue
}

$replacements = [ordered]@{
    "com.autumnwind.botb" = "com.sharktower.bloodonthesharktower"
    "blood-on-the-blocktower" = "blood_on_the_sharktower"
    'Commands.literal("botb")' = 'Commands.literal("bots")'
    'literal("botb")' = 'literal("bots")'
    '"botb_player"' = '"bots_player"'
    '"botb_traveler"' = '"bots_traveler"'
    "message.blood-on-the-blocktower." = "message.blood_on_the_sharktower."
}

$changed = 0
foreach ($file in ($files | Sort-Object FullName -Unique)) {
    $content = Get-Content -LiteralPath $file.FullName -Raw
    $updated = $content
    foreach ($entry in $replacements.GetEnumerator()) {
        $updated = $updated.Replace($entry.Key, $entry.Value)
    }

    if ($updated -ne $content) {
        Set-Content -LiteralPath $file.FullName -Value $updated -NoNewline
        $changed++
        Write-Host "Rewrote $($file.FullName)"
    }
}

Write-Host "Fast-port rewrite complete. Changed $changed file(s)."
Write-Host "Command namespace: /botb -> /bots"
