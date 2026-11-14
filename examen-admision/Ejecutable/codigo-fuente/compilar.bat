@echo off
rem Compila Admissio.exe con el compilador de C# incluido en .NET Framework 4 (Windows).
cd /d "%~dp0"
"%WINDIR%\Microsoft.NET\Framework64\v4.0.30319\csc.exe" -nologo -target:winexe -codepage:65001 -optimize+ -win32icon:admissio.ico -out:..\Admissio.exe -r:System.Windows.Forms.dll Admissio.cs
if %errorlevel%==0 (echo Listo: ..\Admissio.exe) else (echo Error al compilar.)
pause
