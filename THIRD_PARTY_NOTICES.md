# Third-party notices

## NPV Tunnel NPVT1 compatibility tables

Portions of `shared/src/commonMain/kotlin/dev/cluvex/zedsecure/domain/config/NpvtWhiteboxCipher.kt`, including the table data and compatibility transform, are derived from the MIT-licensed project [TheLouisMahdi/npvt-terminal-converter](https://github.com/TheLouisMahdi/npvt-terminal-converter).

MIT License

Copyright (c) 2026 Mahdi Gh

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.


## NPVS v5 format compatibility

The Android passphrase-only NPVS v5 decoder was independently implemented against the public format behavior and interoperability tests documented by [FrontierTM/Pantegnos](https://github.com/FrontierTM/Pantegnos). No Pantegnos source files or white-box table assets are vendored by this decoder. Pantegnos is licensed under AGPL-3.0; ZX-VPN is also distributed under AGPL-3.0. This is a compatibility implementation, not an affiliation or endorsement.

## SlipNet format compatibility

The SlipNet profile import and decryption compatibility layer was implemented with reference to the public export format and behavior described by [anonvector/SlipNet](https://github.com/anonvector/SlipNet) and [FrontierTM/Pantegnos](https://github.com/FrontierTM/Pantegnos). Pantegnos current releases are licensed under AGPL-3.0; its source files are not vendored into this repository. This compatibility layer is part of the AGPL-3.0 ZX-VPN project. These projects are independent and there is no affiliation or endorsement implied.
