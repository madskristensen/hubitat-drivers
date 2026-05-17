# One-time: List in HPM master directory

```powershell
gh repo fork HubitatCommunity/hubitat-packagerepositories --clone --remote
cd hubitat-packagerepositories
git checkout -b add-madskristensen-hubitat-drivers

# Edit repositories.json and insert the JSON object from:
# ..\hubitat-drivers\release-tools\repositories-json-addition.json
# Suggested location: after "Lyle Pakula (@lpakula)" and before
# "Marco Felicio (maffpt@gmail.com)" inside the "repositories" array.
# Verify with:
git diff

git add repositories.json
git commit -m "Add @madskristensen / hubitat-drivers"
git push -u origin add-madskristensen-hubitat-drivers
gh pr create --repo HubitatCommunity/hubitat-packagerepositories --title "Add @madskristensen — hubitat-drivers" --body-file ..\hubitat-drivers\release-tools\hpm-community-pr-body.md
```
