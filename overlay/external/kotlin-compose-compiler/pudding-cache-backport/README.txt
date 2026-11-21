Compose compiler 2.2.0 stability inference cache backport
Upstream: https://github.com/JetBrains/kotlin/commit/f3999c1f744d9053a4edb1b30064c0f19968860b
Addresses upstream bug 481953005 / 481769383: exponential repeated type analysis.
Original Maven JAR remains unchanged. Only StabilityInferencer.class differs in the selected hosted JAR. Public ABI unchanged.
Rebuild with: python3 rebuild.py --tree-root <AOSPA source> --output-dir <new scratch directory>
Test evidence: ordinary/generic/cyclic/diamond fixtures produce byte-identical classes; depth25 diamond old timed out45s, patched21.576s.
