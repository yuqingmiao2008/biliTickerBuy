def check():
    import sys
    out = [sys.version.split()[0]]
    try:
        import requests
        out.append("requests " + requests.__version__)
    except Exception as e:
        out.append("requests FAIL: " + repr(e))
    try:
        import httpx
        out.append("httpx " + httpx.__version__)
    except Exception as e:
        out.append("httpx FAIL: " + repr(e))
    return "\n".join(out)