# Stops the emulator if the C drive runs low, because its data files grow while it runs.
param([int]$MinFreeGb = 2, [int]$MaxMinutes = 45)
$until = (Get-Date).AddMinutes($MaxMinutes)
while ((Get-Date) -lt $until) {
    $free = (Get-PSDrive C).Free / 1GB
    if ($free -lt $MinFreeGb) {
        Get-Process qemu*, emulator -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue
        "STOPPED emulator at {0:N1} GB free" -f $free
        exit 1
    }
    Start-Sleep -Seconds 10
}
"guard finished"
