<#
.SYNOPSIS
    Registra o SuperMidia MIDI Player no menu "Abrir com" do Windows.

.DESCRIPTION
    Escreve apenas em HKCU\Software\Classes, no perfil do usuario atual: nao pede
    administrador e nao mexe na maquina inteira.

    Sao tres registros, e os tres sao necessarios:

      1. Os ProgID (SuperMidia.MIDI e SuperMidia.Playlist), que dizem como abrir
         cada tipo de arquivo.
      2. A chave Applications do executavel, com SupportedTypes. E ela que faz o
         programa aparecer na lista do "Abrir com"; so o OpenWithProgids nao
         basta no Windows 11.
      3. O OpenWithProgids de cada extensao, que ACRESCENTA o player as opcoes
         sem roubar o programa padrao de ninguem.

    Depois de rodar, clique com o botao direito no arquivo e escolha "Abrir com".
    O proprio menu do Windows oferece torna-lo padrao, se voce quiser.

    O registro guarda o caminho do executavel. Se a pasta do player mudar de lugar,
    rode este script de novo.

.PARAMETER ExePath
    Caminho do "SuperMidia MIDI Player.exe". O padrao e a pasta onde o pacote
    portatil costuma ficar.

.PARAMETER Remover
    Desfaz o registro.

.EXAMPLE
    .\registrar-associacoes.ps1
    .\registrar-associacoes.ps1 -ExePath "D:\Players\SuperMidia MIDI Player.exe"
    .\registrar-associacoes.ps1 -Remover
#>
[CmdletBinding()]
param(
    [string] $ExePath = "D:\Denis\Music\Software\SuperMidia MIDI Player\SuperMidia MIDI Player.exe",
    [switch] $Remover
)

$ErrorActionPreference = "Stop"

$progId = "SuperMidia.MIDI"
$progIdPlaylist = "SuperMidia.Playlist"
$classes = "HKCU:\Software\Classes"
$nomeAmigavel = "SuperMidia MIDI Player"

# Extensao -> ProgID. O .kar e MIDI com letra embutida; o .m3u8 e a playlist do player.
$extensoes = [ordered]@{
    ".mid"   = $progId
    ".midi"  = $progId
    ".kar"   = $progId
    ".m3u8"  = $progIdPlaylist
    ".m3u"   = $progIdPlaylist
}

$nomeExe = Split-Path $ExePath -Leaf
$chaveApp = Join-Path $classes "Applications\$nomeExe"

function Remove-Chave([string] $caminho, [string] $rotulo) {
    if (Test-Path $caminho) {
        Remove-Item $caminho -Recurse -Force
        Write-Host "removido    $rotulo"
    }
}

if ($Remover) {
    foreach ($extensao in $extensoes.Keys) {
        $caminho = Join-Path $classes "$extensao\OpenWithProgids"
        if (Test-Path $caminho) {
            $id = $extensoes[$extensao]
            if ($null -ne (Get-ItemProperty $caminho).PSObject.Properties[$id]) {
                Remove-ItemProperty $caminho -Name $id -Force
                Write-Host "removido    $extensao -> $id"
            }
        }
    }
    Remove-Chave (Join-Path $classes $progId) $progId
    Remove-Chave (Join-Path $classes $progIdPlaylist) $progIdPlaylist
    Remove-Chave $chaveApp "Applications\$nomeExe"
}
else {
    if (-not (Test-Path $ExePath)) {
        throw "Executavel nao encontrado: $ExePath`nPasse o caminho certo em -ExePath."
    }
    $ExePath = (Resolve-Path $ExePath).Path
    $nomeExe = Split-Path $ExePath -Leaf
    $chaveApp = Join-Path $classes "Applications\$nomeExe"

    # O %1 entre aspas: sem elas, um caminho com espacos chega picado ao programa.
    $linhaComando = ("`"{0}`" `"%1`"" -f $ExePath)
    $linhaIcone = ("`"{0}`",0" -f $ExePath)

    function Set-Comando([string] $raiz) {
        $comando = Join-Path $raiz "shell\open\command"
        New-Item $comando -Force | Out-Null
        Set-ItemProperty $comando -Name "(default)" -Value $linhaComando
    }

    function New-ProgId([string] $id, [string] $rotulo) {
        $caminho = Join-Path $classes $id
        New-Item $caminho -Force | Out-Null
        Set-ItemProperty $caminho -Name "(default)" -Value $rotulo

        $icone = Join-Path $caminho "DefaultIcon"
        New-Item $icone -Force | Out-Null
        Set-ItemProperty $icone -Name "(default)" -Value $linhaIcone

        Set-Comando $caminho
        Write-Host "registrado  $id"
    }

    New-ProgId $progId "Musica MIDI (SuperMidia)"
    New-ProgId $progIdPlaylist "Playlist do SuperMidia"

    # A chave que faz o programa aparecer no "Abrir com".
    New-Item $chaveApp -Force | Out-Null
    Set-ItemProperty $chaveApp -Name "FriendlyAppName" -Value $nomeAmigavel
    Set-Comando $chaveApp

    $tipos = Join-Path $chaveApp "SupportedTypes"
    New-Item $tipos -Force | Out-Null
    foreach ($extensao in $extensoes.Keys) {
        New-ItemProperty $tipos -Name $extensao -Value "" -PropertyType String -Force | Out-Null
    }
    Write-Host "registrado  Applications\$nomeExe"

    foreach ($extensao in $extensoes.Keys) {
        $caminho = Join-Path $classes "$extensao\OpenWithProgids"
        New-Item $caminho -Force | Out-Null
        # Valor vazio: o que conta e o nome do ProgID estar listado aqui.
        New-ItemProperty $caminho -Name $extensoes[$extensao] -Value "" -PropertyType String -Force | Out-Null
        Write-Host "registrado  $extensao -> $($extensoes[$extensao])"
    }
}

# Avisa o Explorer, senao o menu "Abrir com" so muda no proximo logon.
Add-Type -Namespace Shell32 -Name Notificacao -MemberDefinition @"
[System.Runtime.InteropServices.DllImport("shell32.dll")]
public static extern void SHChangeNotify(int eventId, uint flags, IntPtr item1, IntPtr item2);
"@
[Shell32.Notificacao]::SHChangeNotify(0x08000000, 0, [IntPtr]::Zero, [IntPtr]::Zero)

Write-Host ""
if ($Remover) {
    Write-Host "Associacoes removidas."
} else {
    Write-Host "Pronto. Botao direito no arquivo -> Abrir com -> SuperMidia MIDI Player."
    Write-Host "Se o menu ainda nao mostrar, feche e reabra o Explorer."
}
