def check():
    import sys
    out = [sys.version.split()[0]]
    try:
        import pydantic
        out.append("pydantic " + pydantic.VERSION)
    except Exception as e:
        out.append("pydantic FAIL: " + repr(e))
    try:
        import gradio
        out.append("gradio " + gradio.__version__)
    except Exception as e:
        out.append("gradio FAIL: " + repr(e))
    return "\n".join(out)