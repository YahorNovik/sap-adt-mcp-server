# Captures real SAP ADT XML responses as test fixtures for the parser fixes.
# All calls are READ-ONLY (search, data preview SELECT, syntax check of a standard class).
# The password is requested interactively and never written to disk or console.
param(
    [string]$BaseUrl = 'http://localhost:50000',
    [string]$Client  = '001',
    [string]$User    = ''
)
$ErrorActionPreference = 'Stop'
$out  = $PSScriptRoot
$cred = Get-Credential -UserName $User -Message "SAP logon $BaseUrl (client $Client)"
$pair = "{0}:{1}" -f $cred.UserName, $cred.GetNetworkCredential().Password
$auth = 'Basic ' + [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($pair))
$pair = $null
$session = New-Object Microsoft.PowerShell.Commands.WebRequestSession
$q = "sap-client=$Client&sap-language=EN"

function Save-Response($name, $resp) {
    $file = Join-Path $out $name
    [IO.File]::WriteAllText($file, $resp.Content, [Text.UTF8Encoding]::new($false))
    Write-Host ("{0,-28} HTTP {1}  {2} Bytes" -f $name, $resp.StatusCode, $resp.Content.Length)
}

function Invoke-Adt($method, $path, $headers, $body, $contentType) {
    $sep = if ($path.Contains('?')) { '&' } else { '?' }
    $params = @{ Uri = "$BaseUrl$path$sep$q"; Method = $method; WebSession = $session
                 Headers = $headers + @{ Authorization = $auth }; SkipHttpErrorCheck = $true }
    if ($body) { $params.Body = $body; $params.ContentType = $contentType }
    Invoke-WebRequest @params
}

# CSRF token (needed for POST)
$disc = Invoke-Adt GET '/sap/bc/adt/discovery' @{ 'x-csrf-token' = 'Fetch'; Accept = 'application/atomsvc+xml' }
if ($disc.StatusCode -ne 200) { throw "Login fehlgeschlagen: HTTP $($disc.StatusCode)" }
$csrf = @{ 'x-csrf-token' = [string]$disc.Headers['x-csrf-token'] }

# 1) Repository quick search (issue #5a)
Save-Response 'search_quick.xml' (Invoke-Adt GET `
    '/sap/bc/adt/repository/informationsystem/search?operation=quickSearch&query=CL_ABAP_TYPEDESCR&maxResults=3' `
    @{ Accept = 'application/*' })

# 2) Data preview / freestyle SQL (issue #5b)
Save-Response 'datapreview_t000.xml' (Invoke-Adt POST '/sap/bc/adt/datapreview/freestyle?rowNumber=3' `
    ($csrf + @{ Accept = 'application/vnd.sap.adt.datapreview.table.v1+xml' }) `
    'SELECT mandt, mtext FROM t000' 'text/plain; charset=utf-8')

# 3) Syntax check (issue #6): A = payload as the plugin sends it today, B = candidate fix
$uri = '/sap/bc/adt/oo/classes/cl_abap_typedescr'
$hdr = $csrf + @{ Accept = 'application/vnd.sap.adt.checkmessages+xml' }
$ct  = 'application/vnd.sap.adt.checkobjects+xml'
$ns  = 'xmlns:chkrun="http://www.sap.com/adt/checkrun" xmlns:adtcore="http://www.sap.com/adt/core"'
Save-Response 'checkrun_A_current.xml' (Invoke-Adt POST '/sap/bc/adt/checkruns?reporters=abapCheckRun' $hdr `
    "<?xml version=`"1.0`" encoding=`"UTF-8`"?><chkrun:checkObjectList $ns><chkrun:checkObject chkrun:uri=`"$uri/source/main`"/></chkrun:checkObjectList>" $ct)
Save-Response 'checkrun_B_fixed.xml' (Invoke-Adt POST '/sap/bc/adt/checkruns?reporters=abapCheckRun' $hdr `
    "<?xml version=`"1.0`" encoding=`"UTF-8`"?><chkrun:checkObjectList $ns><chkrun:checkObject adtcore:uri=`"$uri`" chkrun:version=`"active`"/></chkrun:checkObjectList>" $ct)

$auth = $null
Write-Host "`nFertig. Dateien liegen in: $out"
