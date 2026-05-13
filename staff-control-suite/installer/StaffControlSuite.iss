; ============================================================
;  Staff Control Suite - Inno Setup Installer Script
;  Build with: ISCC.exe StaffControlSuite.iss
;  Or use the build-and-sign.ps1 script in the repo root.
; ============================================================

#define AppName      "Staff Control Suite"
#define AppVersion   "1.0.0"
#define AppPublisher "Cable Hosting"
#define AppURL       ""
#define AppExeName   "StaffControlSuite.exe"
; Unique AppId - do NOT change after first release (used for upgrade detection)
#define AppId        "{{3F8A2C1D-B47E-4D9A-8E6F-C0A1B2D3E4F5}"

; Source files are in the publish\ output next to the installer\ folder
#define SourceDir    "..\desktop-app\publish"

; ============================================================
[Setup]
AppId={#AppId}
AppName={#AppName}
AppVersion={#AppVersion}
AppVerName={#AppName} {#AppVersion}
AppPublisher={#AppPublisher}
AppPublisherURL={#AppURL}
AppSupportURL={#AppURL}
AppUpdatesURL={#AppURL}

; Install into the current user's AppData - no admin/UAC required
DefaultDirName={localappdata}\{#AppName}
DefaultGroupName={#AppName}
DisableProgramGroupPage=yes

; No admin needed - installs per-user
PrivilegesRequired=lowest

; Single-exe output
OutputDir=dist
OutputBaseFilename=StaffControlSuite-Setup-{#AppVersion}

; Compression
Compression=lzma2/ultra64
SolidCompression=yes

; Wizard style
WizardStyle=modern

; Uninstall display info
UninstallDisplayName={#AppName}
UninstallDisplayIcon={app}\{#AppExeName}

; Version info embedded in the installer exe
VersionInfoVersion={#AppVersion}
VersionInfoCompany={#AppPublisher}
VersionInfoDescription={#AppName} Installer
VersionInfoProductName={#AppName}

; App icon - place AppIcon.ico in the installer\ folder.
; Comment out this line if you don't have an icon yet.
; SetupIconFile=AppIcon.ico

; Close the app if it is running before upgrading
CloseApplications=yes
CloseApplicationsFilter=*.exe
RestartApplications=no

; ============================================================
[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"

; ============================================================
[Tasks]
Name: "desktopicon"; \
  Description: "Create a &desktop shortcut"; \
  GroupDescription: "Additional icons:"; \
  Flags: unchecked

; ============================================================
[Files]
; Main executable (always overwrite)
Source: "{#SourceDir}\{#AppExeName}"; \
  DestDir: "{app}"; \
  Flags: ignoreversion

; All other files from the publish folder (runtime libs, etc.)
Source: "{#SourceDir}\*"; \
  DestDir: "{app}"; \
  Flags: ignoreversion recursesubdirs createallsubdirs; \
  Excludes: "{#AppExeName}"

; ============================================================
[Icons]
; Start menu (per-user)
Name: "{userprograms}\{#AppName}"; \
  FileName: "{app}\{#AppExeName}"; \
  WorkingDir: "{app}"

Name: "{userprograms}\Uninstall {#AppName}"; \
  FileName: "{uninstallexe}"

; Desktop (only if task is selected)
Name: "{userdesktop}\{#AppName}"; \
  FileName: "{app}\{#AppExeName}"; \
  WorkingDir: "{app}"; \
  Tasks: desktopicon

; ============================================================
[Run]
; Option to launch the app after installation
Filename: "{app}\{#AppExeName}"; \
  Description: "Launch {#AppName}"; \
  Flags: nowait postinstall skipifsilent

; ============================================================
[UninstallDelete]
Type: filesandordirs; Name: "{app}\logs"

; ============================================================
[Code]
// Check for existing installation and inform the user before upgrading.
function InitializeSetup(): Boolean;
var
  PrevVersion: String;
begin
  Result := True;

  if RegQueryStringValue(
      HKCU,
      'SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall\{#AppId}_is1',
      'DisplayVersion',
      PrevVersion) then
  begin
    if MsgBox(
        'Version ' + PrevVersion + ' of {#AppName} is already installed.' + #13#10 +
        'The installer will upgrade it in place.' + #13#10#13#10 +
        'Continue?',
        mbInformation, MB_YESNO) = IDNO then
    begin
      Result := False;
    end;
  end;
end;
